import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin, interval, Subscription } from 'rxjs';
import { openPdfWindow, PdfCompanySettings } from '../../shared/pdf-export.util';
import { SettingsService } from '../../core/services/settings.service';
import { AttendanceService } from '../../core/services/attendance.service';
import { EmployeeService } from '../../core/services/employee.service';
import { Esp32Service, TerminalData, ScanEvent } from '../../core/services/esp32.service';
import { CompanySettings } from '../../core/models/settings.model';
import { Employee } from '../../core/models/employee.model';
import { AttendanceRecord as ApiAttendance } from '../../core/models/attendance.model';

interface DisplayRecord {
  rfidBadge: string;
  firstName: string;
  lastName: string;
  position: string;
  department: string;
  entryTime: string | null;
  exitTime: string | null;
  hoursWorked: string;
  status: 'present' | 'absent' | 'retard';
}

interface LiveFeedEntry {
  firstName: string;
  lastName: string;
  rfidBadge: string;
  type: 'entree' | 'sortie';
  timestamp: string;
}

@Component({
  selector: 'app-attendance-list',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './attendance-list.component.html',
  styleUrl: './attendance-list.component.scss'
})
export class AttendanceListComponent implements OnInit, OnDestroy {
  selectedDate = new Date();
  searchValue = '';
  statusFilter = 'tous';
  syncing = false;
  lastSync = 0;
  loading = false;
  private syncInterval: ReturnType<typeof setInterval> | null = null;
  private refreshSub: Subscription | null = null;

  terminals: TerminalData[] = [];
  liveFeed: LiveFeedEntry[] = [];
  settings: CompanySettings | null = null;

  private employees: Employee[] = [];
  private monthRecords: ApiAttendance[] = [];
  allRecords: DisplayRecord[] = [];
  filteredRecords: DisplayRecord[] = [];

  get onlineTerminals(): number {
    return this.terminals.filter(t => t.status === 'online').length;
  }

  get totalTerminals(): number {
    return this.terminals.length;
  }

  get totalEmployes(): number {
    return this.allRecords.length;
  }

  get presents(): number {
    return this.allRecords.filter(r => r.status === 'present').length;
  }

  get absents(): number {
    return this.allRecords.filter(r => r.status === 'absent').length;
  }

  get retards(): number {
    return this.allRecords.filter(r => r.status === 'retard').length;
  }

  get selectedDateFormatted(): string {
    const days = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
    const months = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
    const d = this.selectedDate;
    return `${days[d.getDay()]} ${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
  }

  get recordCountLabel(): string {
    const d = this.selectedDate;
    const months = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
    return `${this.filteredRecords.length} enregistrement(s) — ${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
  }

  constructor(
    private settingsService: SettingsService,
    private attendanceService: AttendanceService,
    private employeeService: EmployeeService,
    private esp32Service: Esp32Service
  ) {}

  ngOnInit(): void {
    this.settingsService.get().subscribe({
      next: (res) => this.settings = res.data,
      error: () => {}
    });
    this.loadData();
    this.loadTerminals();
    this.loadLiveFeed();

    this.syncInterval = setInterval(() => {
      this.lastSync++;
    }, 60000);

    this.refreshSub = interval(10000).subscribe(() => {
      this.loadTerminals();
      this.loadLiveFeed();
    });
  }

  ngOnDestroy(): void {
    if (this.syncInterval) clearInterval(this.syncInterval);
    if (this.refreshSub) this.refreshSub.unsubscribe();
  }

  loadData(): void {
    this.loading = true;
    const month = this.selectedDate.getMonth() + 1;
    const year = this.selectedDate.getFullYear();

    forkJoin({
      employees: this.employeeService.getAll(undefined, undefined, 0, 200),
      attendance: this.attendanceService.getByMonth(month, year)
    }).subscribe({
      next: (res) => {
        this.employees = res.employees.data?.content || [];
        this.monthRecords = res.attendance.data || [];
        this.buildDisplayRecords();
        this.loading = false;
      },
      error: () => {
        this.allRecords = [];
        this.filteredRecords = [];
        this.loading = false;
      }
    });
  }

  loadTerminals(): void {
    this.esp32Service.getTerminals().subscribe({
      next: (terminals) => this.terminals = terminals,
      error: () => {}
    });
  }

  loadLiveFeed(): void {
    this.esp32Service.getRecentScans().subscribe({
      next: (scans) => {
        this.liveFeed = scans.map(s => {
          const parts = s.employeeName ? s.employeeName.split(' ') : [''];
          return {
            firstName: parts[0] || '',
            lastName: parts.slice(1).join(' ') || '',
            rfidBadge: s.rfidUid || '—',
            type: s.action === 'CHECK_IN' ? 'entree' as const : 'sortie' as const,
            timestamp: s.time || ''
          };
        });
      },
      error: () => {
        this.buildLiveFeedFromAttendance();
      }
    });
  }

  private buildLiveFeedFromAttendance(): void {
    const dateStr = this.formatDateISO(this.selectedDate);
    const dayRecords = this.monthRecords.filter(r => r.date === dateStr);
    const empMap = new Map<number, Employee>();
    this.employees.forEach(e => empMap.set(e.id, e));

    const entries: LiveFeedEntry[] = [];
    for (const rec of dayRecords) {
      const emp = empMap.get(rec.employeeId);
      if (!emp) continue;
      if (rec.checkOut) {
        entries.push({
          firstName: emp.firstName,
          lastName: emp.lastName,
          rfidBadge: emp.rfidUid || '—',
          type: 'sortie',
          timestamp: this.extractTime(rec.checkOut) || ''
        });
      }
      if (rec.checkIn) {
        entries.push({
          firstName: emp.firstName,
          lastName: emp.lastName,
          rfidBadge: emp.rfidUid || '—',
          type: 'entree',
          timestamp: this.extractTime(rec.checkIn) || ''
        });
      }
    }
    entries.sort((a, b) => b.timestamp.localeCompare(a.timestamp));
    this.liveFeed = entries.slice(0, 10);
  }

  private buildDisplayRecords(): void {
    const dateStr = this.formatDateISO(this.selectedDate);
    const dayRecords = this.monthRecords.filter(r => r.date === dateStr);
    const recordsByEmployee = new Map<number, ApiAttendance>();
    dayRecords.forEach(r => recordsByEmployee.set(r.employeeId, r));

    const activeEmployees = this.employees.filter(e => e.status === 'ACTIF' || e.status === 'CONGE');

    this.allRecords = activeEmployees.map(emp => {
      const rec = recordsByEmployee.get(emp.id);
      if (rec) {
        const entryTime = rec.checkIn ? this.extractTime(rec.checkIn) : null;
        const exitTime = rec.checkOut ? this.extractTime(rec.checkOut) : null;
        const hoursWorked = rec.workedHours > 0 ? this.formatHours(rec.workedHours) : '0h00';
        const status = this.mapStatus(rec.status, rec.lateMinutes);
        return {
          rfidBadge: emp.rfidUid || '—',
          firstName: emp.firstName,
          lastName: emp.lastName,
          position: emp.position || '—',
          department: emp.department || '—',
          entryTime,
          exitTime,
          hoursWorked,
          status
        };
      }
      return {
        rfidBadge: emp.rfidUid || '—',
        firstName: emp.firstName,
        lastName: emp.lastName,
        position: emp.position || '—',
        department: emp.department || '—',
        entryTime: null,
        exitTime: null,
        hoursWorked: '0h00',
        status: 'absent' as const
      };
    });

    this.applyFilters();
  }

  private mapStatus(apiStatus: string, lateMinutes: number): 'present' | 'absent' | 'retard' {
    if (apiStatus === 'ABSENT') return 'absent';
    if (lateMinutes > 0) return 'retard';
    return 'present';
  }

  private extractTime(dateTimeStr: string | null): string | null {
    if (!dateTimeStr) return null;
    const idx = dateTimeStr.indexOf('T');
    if (idx === -1) return null;
    return dateTimeStr.substring(idx + 1, idx + 6);
  }

  private formatHours(hours: number): string {
    const h = Math.floor(hours);
    const m = Math.round((hours - h) * 60);
    return `${h}h${String(m).padStart(2, '0')}`;
  }

  private formatDateISO(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  applyFilters(): void {
    this.filteredRecords = this.allRecords.filter(record => {
      const matchesSearch = !this.searchValue ||
        `${record.firstName} ${record.lastName}`.toLowerCase().includes(this.searchValue.toLowerCase()) ||
        record.rfidBadge.toLowerCase().includes(this.searchValue.toLowerCase());
      const matchesStatus = this.statusFilter === 'tous' || record.status === this.statusFilter;
      return matchesSearch && matchesStatus;
    });
  }

  onSearch(event: Event): void {
    this.searchValue = (event.target as HTMLInputElement).value;
    this.applyFilters();
  }

  filterByStatus(status: string): void {
    this.statusFilter = status;
    this.applyFilters();
  }

  onDateChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.value) {
      const parts = input.value.split('-');
      this.selectedDate = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
      this.loadData();
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'present': return 'Présent';
      case 'retard': return 'Retard';
      case 'absent': return 'Absent';
      default: return status;
    }
  }

  onSync(): void {
    if (this.syncing) return;
    this.syncing = true;
    this.lastSync = 0;

    forkJoin({
      employees: this.employeeService.getAll(undefined, undefined, 0, 200),
      attendance: this.attendanceService.getByMonth(this.selectedDate.getMonth() + 1, this.selectedDate.getFullYear()),
      terminals: this.esp32Service.getTerminals(),
      scans: this.esp32Service.getRecentScans()
    }).subscribe({
      next: (res) => {
        this.employees = res.employees.data?.content || [];
        this.monthRecords = res.attendance.data || [];
        this.terminals = res.terminals || [];
        this.liveFeed = (res.scans || []).map((s: any) => {
          const parts = s.employeeName ? s.employeeName.split(' ') : [''];
          return {
            firstName: parts[0] || '',
            lastName: parts.slice(1).join(' ') || '',
            rfidBadge: s.rfidUid || '—',
            type: s.action === 'CHECK_IN' ? 'entree' as const : 'sortie' as const,
            timestamp: s.time || ''
          };
        });
        this.buildDisplayRecords();
        this.syncing = false;
      },
      error: () => {
        this.syncing = false;
        this.loadData();
        this.loadTerminals();
        this.loadLiveFeed();
      }
    });
  }

  getSelectedDateInput(): string {
    return this.formatDateISO(this.selectedDate);
  }

  formatUptime(seconds: number | null): string {
    if (!seconds) return '—';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

  formatMemory(bytes: number | null): string {
    if (!bytes) return '—';
    return `${Math.round(bytes / 1024)} Ko`;
  }

  formatSignal(rssi: number | null): string {
    if (rssi === null || rssi === undefined) return '—';
    if (rssi > -50) return 'Excellent';
    if (rssi > -65) return 'Bon';
    if (rssi > -75) return 'Moyen';
    return 'Faible';
  }

  formatLastPing(lastPing: string | null): string {
    if (!lastPing) return 'Jamais';
    const pingDate = new Date(lastPing);
    const now = new Date();
    const diffMs = now.getTime() - pingDate.getTime();
    const diffSec = Math.floor(diffMs / 1000);
    if (diffSec < 10) return `Il y a ${diffSec}s`;
    if (diffSec < 60) return `Il y a ${diffSec}s`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `Il y a ${diffMin}m`;
    const diffH = Math.floor(diffMin / 60);
    return `Il y a ${diffH}h`;
  }

  exportPdf(): void {
    const months = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
    const d = this.selectedDate;
    const periodLabel = `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;

    const rows = this.filteredRecords.map(r => {
      const statusColor = r.status === 'present' ? '#059669' : r.status === 'retard' ? '#D97706' : '#DC2626';
      return `<tr><td>${r.firstName} ${r.lastName}</td><td>${r.rfidBadge}</td><td>${r.position}</td><td>${r.entryTime || '—'}</td><td>${r.exitTime || '—'}</td><td>${r.hoursWorked}</td><td style="color:${statusColor};font-weight:600">${this.getStatusLabel(r.status)}</td></tr>`;
    }).join('');

    const contentHtml = `
      <div class="section-title">État de Présence — ${periodLabel}</div>
      <table>
        <thead><tr><th>Employé</th><th>Badge RFID</th><th>Poste</th><th>Entrée</th><th>Sortie</th><th>Heures</th><th>Statut</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    `;

    openPdfWindow('État de Présence', periodLabel, contentHtml, this.settings as PdfCompanySettings);
  }
}
