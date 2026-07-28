import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Employee } from '../../core/models/employee.model';
import { AttendanceService } from '../../core/services/attendance.service';
import { LeaveService } from '../../core/services/leave.service';
import { PayrollService } from '../../core/services/payroll.service';
import { SettingsService } from '../../core/services/settings.service';
import { AttendanceRecord } from '../../core/models/attendance.model';
import { LeaveRequest } from '../../core/models/leave.model';
import { PayrollItemResponse } from '../../core/models/payroll.model';
import { CompanySettings } from '../../core/models/settings.model';
import { openPdfWindow, PdfCompanySettings } from '../../shared/pdf-export.util';

@Component({
  selector: 'app-employee-detail-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatIconModule],
  styleUrl: './employee-detail-dialog.component.scss',
  template: `
    <div class="detail-dialog">
      <div class="dialog-header">
        <div class="header-left">
          @if (data.photo) {
            <img [src]="data.photo" class="avatar-img-lg" [alt]="data.firstName">
          } @else {
            <div class="avatar-large" [style.background]="getAvatarColor(data.firstName + data.lastName)">
              {{ getInitials(data.firstName, data.lastName) }}
            </div>
          }
          <div>
            <h2 class="dialog-title">{{ data.firstName }} {{ data.lastName }}</h2>
            <p class="dialog-subtitle">{{ data.position || '—' }} · {{ data.department || '—' }}</p>
          </div>
        </div>
        <div class="header-right">
          <span class="status-badge" [ngClass]="'status-' + (data.status || 'ACTIF').toLowerCase()">
            {{ getStatusLabel(data.status) }}
          </span>
          <button class="export-btn" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon>
            PDF
          </button>
          <button class="close-btn" mat-dialog-close>
            <mat-icon>close</mat-icon>
          </button>
        </div>
      </div>

      <div class="dialog-body">

        <!-- SECTION 1: Informations Personnelles -->
        <div class="info-section">
          <div class="section-header" (click)="toggleSection('personnel')">
            <div class="section-header-left">
              <mat-icon class="section-icon">person</mat-icon>
              <span class="section-heading">Informations Personnelles</span>
            </div>
            <mat-icon class="chevron" [class.open]="openSections['personnel']">expand_more</mat-icon>
          </div>
          @if (openSections['personnel']) {
            <div class="section-body">
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">Prénom</span>
                  <span class="info-value">{{ data.firstName }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Nom</span>
                  <span class="info-value">{{ data.lastName }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Email</span>
                  <span class="info-value">{{ data.email || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Téléphone</span>
                  <span class="info-value">{{ data.phone || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Date de naissance</span>
                  <span class="info-value">{{ formatDateShort(data.birthDate) }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">CIN</span>
                  <span class="info-value">{{ data.cin || '—' }}</span>
                </div>
                <div class="info-item full-width">
                  <span class="info-label">Adresse</span>
                  <span class="info-value">{{ data.address || '—' }}</span>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- SECTION 2: Informations Professionnelles -->
        <div class="info-section">
          <div class="section-header" (click)="toggleSection('pro')">
            <div class="section-header-left">
              <mat-icon class="section-icon">work</mat-icon>
              <span class="section-heading">Informations Professionnelles</span>
            </div>
            <mat-icon class="chevron" [class.open]="openSections['pro']">expand_more</mat-icon>
          </div>
          @if (openSections['pro']) {
            <div class="section-body">
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">Matricule</span>
                  <span class="info-value">{{ data.matricule }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Poste</span>
                  <span class="info-value">{{ data.position || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Département</span>
                  <span class="info-value">{{ data.department || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Type de contrat</span>
                  <span class="info-value">{{ data.contractType || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Date d'embauche</span>
                  <span class="info-value">{{ formatDateShort(data.hiringDate) }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">RFID UID</span>
                  <span class="info-value">{{ data.rfidUid || 'Non assigné' }}</span>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- SECTION 3: Horaires de Travail -->
        <div class="info-section">
          <div class="section-header" (click)="toggleSection('horaires')">
            <div class="section-header-left">
              <mat-icon class="section-icon">schedule</mat-icon>
              <span class="section-heading">Horaires de Travail</span>
            </div>
            <mat-icon class="chevron" [class.open]="openSections['horaires']">expand_more</mat-icon>
          </div>
          @if (openSections['horaires']) {
            <div class="section-body">
              <div class="schedule-grid">
                <div class="schedule-header">
                  <span>Jour</span>
                  <span>Entrée</span>
                  <span>Sortie</span>
                  <span>Durée</span>
                </div>
                @for (day of scheduleDays; track day.key) {
                  <div class="schedule-row" [class.inactive]="!day.enabled">
                    <span class="day-name">{{ day.label }}</span>
                    @if (day.enabled) {
                      <span>{{ day.start }}</span>
                      <span>{{ day.end }}</span>
                      <span class="duration">{{ day.duration }}</span>
                    } @else {
                      <span class="off-label" style="grid-column: span 3">Repos</span>
                    }
                  </div>
                }
              </div>
              <div class="schedule-summary">
                <span>Total hebdomadaire : <strong>{{ weeklyHours }}</strong></span>
                <span>Mensuel estimé : <strong>{{ monthlyHours }}h</strong></span>
              </div>
            </div>
          }
        </div>

        <!-- SECTION 4: Salaire & Primes -->
        <div class="info-section">
          <div class="section-header" (click)="toggleSection('salaire')">
            <div class="section-header-left">
              <mat-icon class="section-icon">payments</mat-icon>
              <span class="section-heading">Salaire &amp; Primes</span>
            </div>
            <mat-icon class="chevron" [class.open]="openSections['salaire']">expand_more</mat-icon>
          </div>
          @if (openSections['salaire']) {
            <div class="section-body">
              <div class="salary-grid">
                <div class="salary-card base">
                  <span class="salary-label">Base</span>
                  <span class="salary-val blue">{{ fmt(data.baseSalary) }} DT</span>
                </div>
                <div class="salary-card">
                  <span class="salary-label">Transport</span>
                  <span class="salary-val">{{ fmt(data.primeTransport) }} DT</span>
                </div>
                <div class="salary-card">
                  <span class="salary-label">Rendement</span>
                  <span class="salary-val">{{ fmt(data.primePerformance) }} DT</span>
                </div>
                <div class="salary-card">
                  <span class="salary-label">Autres</span>
                  <span class="salary-val">{{ fmt(data.primeOther) }} DT</span>
                </div>
                <div class="salary-card total">
                  <span class="salary-label">Total Brut</span>
                  <span class="salary-val green">{{ fmt(totalGross) }} DT</span>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- SECTION 5: Droits de Congés -->
        <div class="info-section">
          <div class="section-header" (click)="toggleSection('conges')">
            <div class="section-header-left">
              <mat-icon class="section-icon">beach_access</mat-icon>
              <span class="section-heading">Droits de Congés</span>
            </div>
            <mat-icon class="chevron" [class.open]="openSections['conges']">expand_more</mat-icon>
          </div>
          @if (openSections['conges']) {
            <div class="section-body">
              <div class="leave-rights-grid">
                <div class="leave-right-card">
                  <div class="leave-icon annual">
                    <mat-icon style="color:#2563EB">beach_access</mat-icon>
                  </div>
                  <span class="leave-type-name">Congé Annuel</span>
                  <span class="leave-days">{{ data.annualLeaveDays ?? 0 }} jours</span>
                </div>
                <div class="leave-right-card">
                  <div class="leave-icon maternity">
                    <mat-icon style="color:#7C3AED">child_care</mat-icon>
                  </div>
                  <span class="leave-type-name">Maternité</span>
                  <span class="leave-days">{{ data.maternityLeaveDays ?? 0 }} jours</span>
                </div>
                <div class="leave-right-card">
                  <div class="leave-icon paternity">
                    <mat-icon style="color:#0891B2">family_restroom</mat-icon>
                  </div>
                  <span class="leave-type-name">Paternité</span>
                  <span class="leave-days">{{ data.paternityLeaveDays ?? 0 }} jours</span>
                </div>
              </div>
            </div>
          }
        </div>

        <div class="section-separator"></div>

        <!-- PERIOD SELECTOR -->
        <div class="month-selector">
          <label class="selector-label">Période</label>
          <div class="selector-row">
            <select class="period-select" [(ngModel)]="selectedMonth" (change)="loadHistoryData()">
              @for (m of months; track m.value) {
                <option [value]="m.value">{{ m.label }}</option>
              }
            </select>
            <select class="period-select year-select" [(ngModel)]="selectedYear" (change)="loadHistoryData()">
              @for (y of years; track y) {
                <option [value]="y">{{ y }}</option>
              }
            </select>
          </div>
        </div>

        <!-- HISTORIQUE DE PRÉSENCE -->
        <div class="history-section">
          <div class="section-header static">
            <div class="section-header-left">
              <mat-icon class="section-icon">history</mat-icon>
              <span class="section-heading">Historique de Présence</span>
            </div>
          </div>
          @if (attendanceLoading) {
            <div class="loading-text">Chargement...</div>
          } @else if (attendanceRecords.length === 0) {
            <div class="empty-text">Aucun pointage pour cette période</div>
          } @else {
            <div class="history-table">
              <div class="history-header attendance-header">
                <span>Date</span>
                <span>Entrée</span>
                <span>Sortie</span>
                <span>Durée</span>
                <span>Retard</span>
                <span>Statut</span>
              </div>
              @for (r of attendanceRecords; track r.date) {
                <div class="history-row attendance-row">
                  <span>{{ formatDateShort(r.date) }}</span>
                  <span>{{ formatTime(r.checkIn) }}</span>
                  <span>{{ formatTime(r.checkOut) }}</span>
                  <span>{{ r.workedHours ? r.workedHours + 'h' : '—' }}</span>
                  <span>{{ r.lateMinutes ? r.lateMinutes + 'min' : '—' }}</span>
                  <span class="status-tag" [ngClass]="getAttendanceTag(r.status)">{{ getAttendanceLabel(r.status) }}</span>
                </div>
              }
            </div>
          }
        </div>

        <!-- DEMANDES DE CONGÉ -->
        <div class="history-section">
          <div class="section-header static">
            <div class="section-header-left">
              <mat-icon class="section-icon">beach_access</mat-icon>
              <span class="section-heading">Demandes de Congé</span>
            </div>
          </div>
          @if (leaveLoading) {
            <div class="loading-text">Chargement...</div>
          } @else if (leaveRecords.length === 0) {
            <div class="empty-text">Aucune demande de congé</div>
          } @else {
            <div class="history-table">
              <div class="history-header leave-header">
                <span>Type</span>
                <span>Début</span>
                <span>Fin</span>
                <span>Durée</span>
                <span>Statut</span>
              </div>
              @for (l of leaveRecords; track l.id) {
                <div class="history-row leave-row">
                  <span>{{ l.leaveType }}</span>
                  <span>{{ formatDateShort(l.startDate) }}</span>
                  <span>{{ formatDateShort(l.endDate) }}</span>
                  <span>{{ l.days }} j</span>
                  <span class="status-tag" [ngClass]="getLeaveTag(l.status)">{{ l.status }}</span>
                </div>
              }
            </div>
          }
        </div>

        <!-- HISTORIQUE DE PAIEMENT -->
        <div class="history-section">
          <div class="section-header static">
            <div class="section-header-left">
              <mat-icon class="section-icon">payments</mat-icon>
              <span class="section-heading">Historique de Paiement</span>
            </div>
          </div>
          @if (payrollLoading) {
            <div class="loading-text">Chargement...</div>
          } @else if (!payrollItem) {
            <div class="empty-text">Aucune donnée de paie pour cette période</div>
          } @else {
            <div class="payroll-summary-grid">
              <div class="payroll-kpi">
                <span class="kpi-val green">{{ fmt(payrollItem.totalGross) }} DT</span>
                <span class="kpi-lbl">Brut</span>
              </div>
              <div class="payroll-kpi">
                <span class="kpi-val red">{{ fmt(payrollItem.totalDeductions) }} DT</span>
                <span class="kpi-lbl">Retenues</span>
              </div>
              <div class="payroll-kpi">
                <span class="kpi-val blue">{{ fmt(payrollItem.netSalary) }} DT</span>
                <span class="kpi-lbl">Net</span>
              </div>
              <div class="payroll-kpi">
                <span class="kpi-val">{{ payrollItem.daysWorked }}</span>
                <span class="kpi-lbl">Jours travaillés</span>
              </div>
            </div>
            <div class="history-table">
              <div class="history-header payroll-header">
                <span>Élément</span>
                <span>Montant</span>
              </div>
              <div class="history-row payroll-row">
                <span>Salaire de base</span>
                <span>{{ fmt(payrollItem.baseSalary) }} DT</span>
              </div>
              <div class="history-row payroll-row">
                <span>Prime Transport</span>
                <span>{{ fmt(payrollItem.primeTransport) }} DT</span>
              </div>
              <div class="history-row payroll-row">
                <span>Prime Rendement</span>
                <span>{{ fmt(payrollItem.primePerformance) }} DT</span>
              </div>
              <div class="history-row payroll-row">
                <span>Autres Primes</span>
                <span>{{ fmt(payrollItem.primeOther) }} DT</span>
              </div>
              @if (payrollItem.overtimeAmount > 0) {
                <div class="history-row payroll-row highlight">
                  <span>Heures Supplémentaires ({{ payrollItem.overtimeHours }}h)</span>
                  <span>+{{ fmt(payrollItem.overtimeAmount) }} DT</span>
                </div>
              }
              @if (payrollItem.lateDeduction > 0) {
                <div class="history-row payroll-row deduct">
                  <span>Retard ({{ payrollItem.lateMinutes }} min)</span>
                  <span>-{{ fmt(payrollItem.lateDeduction) }} DT</span>
                </div>
              }
              @if (payrollItem.absenceDeduction > 0) {
                <div class="history-row payroll-row deduct">
                  <span>Absence ({{ payrollItem.daysAbsent }} j)</span>
                  <span>-{{ fmt(payrollItem.absenceDeduction) }} DT</span>
                </div>
              }
              <div class="history-row payroll-row deduct">
                <span>CNSS Employé</span>
                <span>-{{ fmt(payrollItem.cnssDeduction) }} DT</span>
              </div>
              <div class="history-row payroll-row deduct">
                <span>Assurance Maladie</span>
                <span>-{{ fmt(payrollItem.assuranceDeduction) }} DT</span>
              </div>
              <div class="history-row payroll-row deduct">
                <span>Impôt sur le Revenu</span>
                <span>-{{ fmt(payrollItem.irDeduction) }} DT</span>
              </div>
              <div class="history-row payroll-row total">
                <span>Salaire Net</span>
                <span>{{ fmt(payrollItem.netSalary) }} DT</span>
              </div>
            </div>
          }
        </div>

      </div>
    </div>
  `
})
export class EmployeeDetailDialogComponent implements OnInit {
  selectedMonth: number;
  selectedYear: number;

  months = [
    { value: 1, label: 'Janvier' }, { value: 2, label: 'Février' },
    { value: 3, label: 'Mars' }, { value: 4, label: 'Avril' },
    { value: 5, label: 'Mai' }, { value: 6, label: 'Juin' },
    { value: 7, label: 'Juillet' }, { value: 8, label: 'Août' },
    { value: 9, label: 'Septembre' }, { value: 10, label: 'Octobre' },
    { value: 11, label: 'Novembre' }, { value: 12, label: 'Décembre' },
  ];
  years: number[] = [];

  attendanceRecords: AttendanceRecord[] = [];
  leaveRecords: LeaveRequest[] = [];
  payrollItem: PayrollItemResponse | null = null;

  attendanceLoading = false;
  leaveLoading = false;
  payrollLoading = false;

  private settings: CompanySettings | null = null;

  openSections: Record<string, boolean> = {
    personnel: true,
    pro: true,
    horaires: true,
    salaire: true,
    conges: true,
  };

  scheduleDays: { key: string; label: string; enabled: boolean; start: string; end: string; duration: string }[] = [];
  weeklyHours = '';
  monthlyHours = 0;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: Employee,
    private attendanceService: AttendanceService,
    private leaveService: LeaveService,
    private payrollService: PayrollService,
    private settingsService: SettingsService
  ) {
    const now = new Date();
    this.selectedMonth = now.getMonth() + 1;
    this.selectedYear = now.getFullYear();
    const cy = this.selectedYear;
    this.years = [cy - 2, cy - 1, cy, cy + 1];
  }

  ngOnInit(): void {
    this.settingsService.get().subscribe({ next: (res) => this.settings = res.data || null });
    this.parseSchedule();
    this.loadHistoryData();
  }

  loadHistoryData(): void {
    this.loadAttendance();
    this.loadLeaves();
    this.loadPayroll();
  }

  toggleSection(key: string): void {
    this.openSections[key] = !this.openSections[key];
  }

  get totalGross(): number {
    return (this.data.baseSalary || 0) + (this.data.primeTransport || 0) + (this.data.primePerformance || 0) + (this.data.primeOther || 0);
  }

  parseSchedule(): void {
    const daysDef: { key: string; label: string }[] = [
      { key: 'LUN', label: 'Lundi' },
      { key: 'MAR', label: 'Mardi' },
      { key: 'MER', label: 'Mercredi' },
      { key: 'JEU', label: 'Jeudi' },
      { key: 'VEN', label: 'Vendredi' },
      { key: 'SAM', label: 'Samedi' },
      { key: 'DIM', label: 'Dimanche' },
    ];

    let parsed: any = null;
    if (this.data.weeklySchedule) {
      try {
        parsed = JSON.parse(this.data.weeklySchedule);
      } catch {
        parsed = null;
      }
    }

    let totalMinutes = 0;
    this.scheduleDays = daysDef.map(d => {
      const dayData = parsed?.[d.key];
      if (dayData && dayData.enabled !== false && dayData.start && dayData.end) {
        const mins = this.timeDiffMinutes(dayData.start, dayData.end);
        totalMinutes += Math.max(mins, 0);
        return {
          key: d.key,
          label: d.label,
          enabled: true,
          start: dayData.start || '',
          end: dayData.end || '',
          duration: this.minutesToHM(Math.max(mins, 0)),
        };
      }
      return { key: d.key, label: d.label, enabled: false, start: '', end: '', duration: '' };
    });

    this.weeklyHours = this.minutesToHM(totalMinutes);
    this.monthlyHours = Math.round((totalMinutes / 60) * 52 / 12);
  }

  private timeDiffMinutes(start: string, end: string): number {
    const [sh, sm] = start.split(':').map(Number);
    const [eh, em] = end.split(':').map(Number);
    return (eh * 60 + em) - (sh * 60 + sm);
  }

  private minutesToHM(mins: number): string {
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return `${h}h${m > 0 ? ' ' + String(m).padStart(2, '0') + 'min' : ''}`;
  }

  private loadAttendance(): void {
    this.attendanceLoading = true;
    this.attendanceService.getByMonth(this.selectedMonth, this.selectedYear, this.data.id).subscribe({
      next: (res) => {
        this.attendanceRecords = (res.data || []).sort((a, b) => b.date.localeCompare(a.date));
        this.attendanceLoading = false;
      },
      error: () => { this.attendanceRecords = []; this.attendanceLoading = false; }
    });
  }

  private loadLeaves(): void {
    this.leaveLoading = true;
    this.leaveService.getByEmployeeId(this.data.id).subscribe({
      next: (res) => {
        const all = res.data || [];
        this.leaveRecords = all.filter(l => {
          const d = new Date(l.startDate);
          return d.getMonth() + 1 === this.selectedMonth && d.getFullYear() === this.selectedYear;
        });
        this.leaveLoading = false;
      },
      error: () => { this.leaveRecords = []; this.leaveLoading = false; }
    });
  }

  private loadPayroll(): void {
    this.payrollLoading = true;
    this.payrollItem = null;
    this.payrollService.getByMonth(this.selectedMonth, this.selectedYear).subscribe({
      next: (res) => {
        const payroll = res.data;
        if (payroll?.items) {
          this.payrollItem = payroll.items.find(i => i.employeeId === this.data.id) || null;
        }
        this.payrollLoading = false;
      },
      error: () => { this.payrollLoading = false; }
    });
  }

  fmt(val: number | null | undefined): string {
    if (val == null) return '0,000';
    return val.toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 });
  }

  formatDateShort(d: string | undefined): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  formatTime(val: string | null | undefined): string {
    if (!val) return '—';
    const idx = val.indexOf('T');
    if (idx === -1) return val;
    return val.substring(idx + 1, idx + 6);
  }

  getInitials(firstName: string, lastName: string): string {
    return (firstName?.charAt(0) || '') + (lastName?.charAt(0) || '');
  }

  getAvatarColor(name: string): string {
    const colors = ['#2563eb', '#8b5cf6', '#22c55e', '#f59e0b', '#ec4899', '#6b7280', '#ef4444', '#06b6d4'];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIF': return 'Actif';
      case 'CONGE': return 'Congé';
      case 'INACTIF': return 'Inactif';
      default: return status;
    }
  }

  getAttendanceTag(status: string): string {
    switch (status) {
      case 'PRESENT': return 'tag-present';
      case 'PARTIAL': return 'tag-partial';
      case 'ABSENT': return 'tag-absent';
      default: return 'tag-present';
    }
  }

  getAttendanceLabel(status: string): string {
    switch (status) {
      case 'PRESENT': return 'Présent';
      case 'PARTIAL': return 'Partiel';
      case 'ABSENT': return 'Absent';
      default: return status;
    }
  }

  getLeaveTag(status: string): string {
    switch (status) {
      case 'Approuvé': return 'tag-approved';
      case 'En cours': return 'tag-pending';
      case 'Refusé': return 'tag-refused';
      default: return 'tag-pending';
    }
  }

  exportPdf(): void {
    const monthLabel = this.months.find(m => m.value === this.selectedMonth)?.label || '';
    const period = `${monthLabel} ${this.selectedYear}`;
    const emp = this.data;

    let attendanceHtml = '';
    if (this.attendanceRecords.length > 0) {
      const rows = this.attendanceRecords.map(r =>
        `<tr>
          <td>${this.formatDateShort(r.date)}</td>
          <td>${this.formatTime(r.checkIn)}</td>
          <td>${this.formatTime(r.checkOut)}</td>
          <td>${r.workedHours ? r.workedHours + 'h' : '—'}</td>
          <td>${r.lateMinutes ? r.lateMinutes + ' min' : '—'}</td>
          <td><span style="color:${this.getAttendanceColor(r.status)};font-weight:600">${this.getAttendanceLabel(r.status)}</span></td>
        </tr>`
      ).join('');
      attendanceHtml = `
        <h4 class="section-title">Historique de Présence</h4>
        <table>
          <thead><tr><th>Date</th><th>Entrée</th><th>Sortie</th><th>Durée</th><th>Retard</th><th>Statut</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>`;
    } else {
      attendanceHtml = `<h4 class="section-title">Historique de Présence</h4><p style="color:#94A3B8;font-size:13px">Aucun pointage pour cette période.</p>`;
    }

    let leaveHtml = '';
    if (this.leaveRecords.length > 0) {
      const rows = this.leaveRecords.map(l =>
        `<tr>
          <td>${l.leaveType}</td>
          <td>${this.formatDateShort(l.startDate)}</td>
          <td>${this.formatDateShort(l.endDate)}</td>
          <td>${l.days} j</td>
          <td><span style="color:${this.getLeaveColor(l.status)};font-weight:600">${l.status}</span></td>
        </tr>`
      ).join('');
      leaveHtml = `
        <h4 class="section-title">Demandes de Congé</h4>
        <table>
          <thead><tr><th>Type</th><th>Début</th><th>Fin</th><th>Durée</th><th>Statut</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>`;
    } else {
      leaveHtml = `<h4 class="section-title">Demandes de Congé</h4><p style="color:#94A3B8;font-size:13px">Aucune demande de congé pour cette période.</p>`;
    }

    let payrollHtml = '';
    if (this.payrollItem) {
      const p = this.payrollItem;
      const payRows = [
        `<tr><td>Salaire de base</td><td style="text-align:right">${this.fmt(p.baseSalary)} DT</td></tr>`,
        `<tr><td>Prime Transport</td><td style="text-align:right">${this.fmt(p.primeTransport)} DT</td></tr>`,
        `<tr><td>Prime Rendement</td><td style="text-align:right">${this.fmt(p.primePerformance)} DT</td></tr>`,
        `<tr><td>Autres Primes</td><td style="text-align:right">${this.fmt(p.primeOther)} DT</td></tr>`,
      ];
      if (p.overtimeAmount > 0) payRows.push(`<tr><td>Heures Supplémentaires (${p.overtimeHours}h)</td><td style="text-align:right;color:#059669">+${this.fmt(p.overtimeAmount)} DT</td></tr>`);
      if (p.lateDeduction > 0) payRows.push(`<tr><td>Retard (${p.lateMinutes} min)</td><td style="text-align:right;color:#DC2626">-${this.fmt(p.lateDeduction)} DT</td></tr>`);
      if (p.absenceDeduction > 0) payRows.push(`<tr><td>Absence (${p.daysAbsent} j)</td><td style="text-align:right;color:#DC2626">-${this.fmt(p.absenceDeduction)} DT</td></tr>`);
      payRows.push(`<tr><td>CNSS Employé</td><td style="text-align:right;color:#DC2626">-${this.fmt(p.cnssDeduction)} DT</td></tr>`);
      payRows.push(`<tr><td>Assurance Maladie</td><td style="text-align:right;color:#DC2626">-${this.fmt(p.assuranceDeduction)} DT</td></tr>`);
      payRows.push(`<tr><td>Impôt sur le Revenu</td><td style="text-align:right;color:#DC2626">-${this.fmt(p.irDeduction)} DT</td></tr>`);
      payRows.push(`<tr style="font-weight:700;background:#F0F9FF"><td>Salaire Net</td><td style="text-align:right;color:#2563EB;font-size:15px">${this.fmt(p.netSalary)} DT</td></tr>`);

      payrollHtml = `
        <h4 class="section-title">Historique de Paiement</h4>
        <table>
          <thead><tr><th>Élément</th><th style="text-align:right">Montant</th></tr></thead>
          <tbody>${payRows.join('')}</tbody>
        </table>`;
    } else {
      payrollHtml = `<h4 class="section-title">Historique de Paiement</h4><p style="color:#94A3B8;font-size:13px">Aucune donnée de paie pour cette période.</p>`;
    }

    const empInfo = `
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:20px;background:#F8FAFC;border:1px solid #E5E7EB;border-radius:10px;padding:16px">
        <div><span style="font-size:11px;color:#94A3B8;text-transform:uppercase;font-weight:600">Matricule</span><br><strong>${emp.matricule || '—'}</strong></div>
        <div><span style="font-size:11px;color:#94A3B8;text-transform:uppercase;font-weight:600">Contrat</span><br><strong>${emp.contractType || '—'}</strong></div>
        <div><span style="font-size:11px;color:#94A3B8;text-transform:uppercase;font-weight:600">Département</span><br><strong>${emp.department || '—'}</strong></div>
        <div><span style="font-size:11px;color:#94A3B8;text-transform:uppercase;font-weight:600">Email</span><br><strong>${emp.email || '—'}</strong></div>
        <div><span style="font-size:11px;color:#94A3B8;text-transform:uppercase;font-weight:600">Téléphone</span><br><strong>${emp.phone || '—'}</strong></div>
        <div><span style="font-size:11px;color:#94A3B8;text-transform:uppercase;font-weight:600">Salaire Base</span><br><strong>${this.fmt(emp.baseSalary)} DT</strong></div>
      </div>`;

    const contentHtml = empInfo + attendanceHtml + leaveHtml + payrollHtml;

    const pdfSettings: PdfCompanySettings | undefined = this.settings ? {
      companyName: this.settings.companyName,
      companyAddress: this.settings.companyAddress,
      companySector: this.settings.companySector,
      companyEmail: this.settings.companyEmail,
      companyPhone: this.settings.companyPhone,
      companyLogo: this.settings.companyLogo,
    } : undefined;

    openPdfWindow(`Fiche Employé — ${emp.firstName} ${emp.lastName}`, period, contentHtml, pdfSettings);
  }

  private getAttendanceColor(status: string): string {
    switch (status) {
      case 'PRESENT': return '#059669';
      case 'PARTIAL': return '#D97706';
      case 'ABSENT': return '#DC2626';
      default: return '#374151';
    }
  }

  private getLeaveColor(status: string): string {
    switch (status) {
      case 'Approuvé': return '#059669';
      case 'En cours': return '#D97706';
      case 'Refusé': return '#DC2626';
      default: return '#374151';
    }
  }
}
