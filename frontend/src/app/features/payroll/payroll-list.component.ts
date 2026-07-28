import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { PaySlipDialogComponent } from './pay-slip-dialog.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { openPdfWindow } from '../../shared/pdf-export.util';
import { PayrollService } from '../../core/services/payroll.service';
import { SettingsService } from '../../core/services/settings.service';
import { NotificationService } from '../../core/services/notification.service';
import { CompanySettings } from '../../core/models/settings.model';
import { PayrollResponse, PayrollItemResponse } from '../../core/models/payroll.model';

@Component({
  selector: 'app-payroll-list',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatIconModule, MatButtonModule],
  templateUrl: './payroll-list.component.html',
  styleUrl: './payroll-list.component.scss'
})
export class PayrollListComponent implements OnInit {
  searchValue = '';
  selectedMonth = new Date().getMonth() + 1;
  selectedYear = new Date().getFullYear();
  viewMode: 'monthly' | 'annual' = 'monthly';
  statusFilter: 'TOUT' | 'PENDING' | 'PAYED' = 'TOUT';
  loading = false;

  payroll: PayrollResponse | null = null;
  filteredRecords: PayrollItemResponse[] = [];
  settings: CompanySettings | null = null;

  monthLabels = [
    'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
    'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'
  ];

  constructor(
    private dialog: MatDialog,
    private payrollService: PayrollService,
    private settingsService: SettingsService,
    private notify: NotificationService,
  ) {}

  ngOnInit(): void {
    this.loadSettings();
    this.loadPayroll();
  }

  loadSettings(): void {
    this.settingsService.get().subscribe({
      next: (res) => this.settings = res.data,
      error: () => {}
    });
  }

  get selectedMonthLabel(): string {
    return `${this.monthLabels[this.selectedMonth - 1]} ${this.selectedYear}`;
  }

  loadPayroll(): void {
    this.loading = true;
    this.payrollService.getByMonth(this.selectedMonth, this.selectedYear).subscribe({
      next: (res) => {
        this.payroll = res.data;
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.payroll = null;
        this.filteredRecords = [];
        this.loading = false;
      }
    });
  }

  payItem(item: PayrollItemResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      panelClass: 'leave-dialog-panel',
      data: {
        title: 'Marquer comme payé',
        message: `Confirmer le paiement du salaire de ${item.firstName} ${item.lastName} ?`,
        confirmLabel: 'Payer',
        type: 'approve'
      } as ConfirmDialogData
    });
    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.payrollService.payItem(item.id).subscribe({
          next: () => {
            this.notify.success('Salaire marqué comme payé');
            this.loadPayroll();
          },
          error: () => this.notify.error('Erreur lors du paiement')
        });
      }
    });
  }

  payAll(): void {
    if (!this.payroll) return;
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      panelClass: 'leave-dialog-panel',
      data: {
        title: 'Tout payer',
        message: `Marquer tous les salaires de ${this.selectedMonthLabel} comme payés ?`,
        confirmLabel: 'Tout Payer',
        type: 'approve'
      } as ConfirmDialogData
    });
    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.payrollService.payAll(this.payroll!.id).subscribe({
          next: () => {
            this.notify.success('Tous les salaires ont été marqués comme payés');
            this.loadPayroll();
          },
          error: () => this.notify.error('Erreur lors du paiement global')
        });
      }
    });
  }

  openPaySlip(item: PayrollItemResponse): void {
    this.dialog.open(PaySlipDialogComponent, {
      width: '820px',
      maxWidth: '92vw',
      maxHeight: '88vh',
      panelClass: 'payslip-dialog-panel',
      data: { item, settings: this.settings, month: this.selectedMonth, year: this.selectedYear }
    });
  }

  onMonthChange(): void {
    this.loadPayroll();
  }

  onSearch(event: Event): void {
    this.searchValue = (event.target as HTMLInputElement).value;
    this.applyFilters();
  }

  onStatusFilter(status: 'TOUT' | 'PENDING' | 'PAYED'): void {
    this.statusFilter = status;
    this.applyFilters();
  }

  applyFilters(): void {
    const all = this.payroll?.items || [];
    this.filteredRecords = all.filter(item => {
      if (this.statusFilter !== 'TOUT' && item.status !== this.statusFilter) return false;
      if (this.searchValue) {
        const q = this.searchValue.toLowerCase();
        const name = `${item.firstName} ${item.lastName}`.toLowerCase();
        const pos = (item.position || '').toLowerCase();
        if (!name.includes(q) && !pos.includes(q)) return false;
      }
      return true;
    });
  }

  get masseSalariale(): number {
    return this.payroll?.totalGross || 0;
  }

  get totalDeductions(): number {
    return this.payroll?.totalDeductions || 0;
  }

  get totalNet(): number {
    return this.payroll?.totalNet || 0;
  }

  get totalPrimes(): number {
    return (this.payroll?.items || []).reduce((sum, r) =>
      sum + (r.primeTransport || 0) + (r.primePerformance || 0) + (r.primeOther || 0), 0);
  }

  get annualMasseSalariale(): number { return this.masseSalariale * 12; }
  get annualSalaireMoyen(): number { return this.payroll ? (this.totalNet / (this.payroll.employeeCount || 1)) * 12 : 0; }
  get annualPrimes(): number { return this.totalPrimes * 12; }
  get annualRetenues(): number { return this.totalDeductions * 12; }

  recalcItem(r: PayrollItemResponse): { cnss: number; assurance: number; ir: number; totalDed: number; net: number } {
    if (!this.settings) return { cnss: r.cnssDeduction || 0, assurance: r.assuranceDeduction || 0, ir: r.irDeduction || 0, totalDed: r.totalDeductions || 0, net: r.netSalary || 0 };
    const s = this.settings;
    const base = r.baseSalary || 0;
    const gross = base + (r.primeTransport || 0) + (r.primePerformance || 0) + (r.primeOther || 0) + (r.overtimeAmount || 0);

    const cnssCeiling = s.cnssCeiling || 5100;
    const cnss = Math.min(base, cnssCeiling) * (s.cnssRate || 11) / 100;
    const assurance = base * (s.assuranceRate || 0.7) / 100;

    const abatement = s.irAbatement || 1000;
    const annualGross = gross * 12;
    const annualCnss = cnss * 12;
    const annualTaxable = Math.max(0, annualGross - annualCnss - abatement);

    const t1 = s.irTranche1 || 5000;
    const t2 = s.irTranche2 || 10000;
    const t3 = s.irTranche3 || 20000;
    const t4 = s.irTranche4 || 30000;
    const r1 = (s.irRate1 || 0) / 100;
    const r2 = (s.irRate2 || 0) / 100;
    const r3 = (s.irRate3 || 0) / 100;
    const r4 = (s.irRate4 || 0) / 100;
    const r5 = (s.irRate5 || 0) / 100;

    let rem = annualTaxable;
    let tax = 0;
    if (rem > 0) { const c = Math.min(rem, t1); tax += c * r1; rem -= c; }
    if (rem > 0) { const c = Math.min(rem, t2 - t1); tax += c * r2; rem -= c; }
    if (rem > 0) { const c = Math.min(rem, t3 - t2); tax += c * r3; rem -= c; }
    if (rem > 0) { const c = Math.min(rem, t4 - t3); tax += c * r4; rem -= c; }
    if (rem > 0) { tax += rem * r5; }
    const ir = tax / 12;

    const cnssR = Math.round(cnss * 100) / 100;
    const assR = Math.round(assurance * 100) / 100;
    const irR = Math.round(ir * 100) / 100;
    const totalDed = cnssR + assR + irR;
    const net = Math.max(0, gross - totalDed);

    return { cnss: cnssR, assurance: assR, ir: irR, totalDed: Math.round(totalDed * 100) / 100, net: Math.round(net * 100) / 100 };
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'PAYED': return 'Payé';
      case 'PENDING': return 'En attente';
      default: return status;
    }
  }

  fmt(n: number): string {
    return (n || 0).toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  onExportPdf(): void {
    const rows = (this.payroll?.items || []).map(r => {
      const rc = this.recalcItem(r);
      const statusColor = r.status === 'PAYED' ? '#059669' : '#D97706';
      const statusLabel = r.status === 'PAYED' ? 'Payé' : 'En attente';
      return `<tr><td>${r.firstName} ${r.lastName}</td><td>${r.position || ''}</td><td>${r.department || ''}</td><td>${this.fmt(r.baseSalary)} DT</td><td>${this.fmt((r.primeTransport || 0) + (r.primePerformance || 0) + (r.primeOther || 0))} DT</td><td>${this.fmt(rc.totalDed)} DT</td><td style="font-weight:700;color:${statusColor}">${this.fmt(rc.net)} DT</td><td style="color:${statusColor};font-weight:600">${statusLabel}</td></tr>`;
    }).join('');

    const contentHtml = `
      <div class="section-title">État des Salaires — ${this.selectedMonthLabel}</div>
      <table>
        <thead><tr><th>Employé</th><th>Poste</th><th>Départ.</th><th>Salaire Base</th><th>Primes</th><th>Retenues</th><th>Net à Payer</th><th>Statut</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    `;
    openPdfWindow('État des Salaires', this.selectedMonthLabel, contentHtml, this.settings as any);
  }
}
