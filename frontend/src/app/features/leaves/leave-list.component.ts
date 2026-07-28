import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { LeaveFormDialogComponent } from './leave-form-dialog.component';
import { LeaveDetailDialogComponent } from './leave-detail-dialog.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LeaveService } from '../../core/services/leave.service';
import { EmployeeService } from '../../core/services/employee.service';
import { SettingsService } from '../../core/services/settings.service';
import { NotificationService } from '../../core/services/notification.service';
import { CompanySettings } from '../../core/models/settings.model';
import { Employee } from '../../core/models/employee.model';
import { LeaveRequest, LeaveBalance } from '../../core/models/leave.model';
import { openPdfWindow, PdfCompanySettings } from '../../shared/pdf-export.util';

@Component({
  selector: 'app-leave-list',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatIconModule, MatButtonModule],
  templateUrl: './leave-list.component.html',
  styleUrl: './leave-list.component.scss'
})
export class LeaveListComponent implements OnInit {
  requests: LeaveRequest[] = [];
  leaveBalance: LeaveBalance[] = [];
  loading = false;

  search = '';
  filterStatus = '';
  filterType = '';
  sortBy = 'newest';
  settings: CompanySettings | null = null;

  employees: Employee[] = [];
  selectedEmployeeId: number | null = null;

  statusOptions = [
    { value: '', label: 'Tous les statuts' },
    { value: 'En cours', label: 'En cours' },
    { value: 'Approuvé', label: 'Approuvé' },
    { value: 'Refusé', label: 'Refusé' },
  ];

  typeOptions = [
    { value: '', label: 'Tous les types' },
    { value: 'Congé Annuel', label: 'Congé Annuel' },
    { value: 'Congé Maladie', label: 'Congé Maladie' },
    { value: 'Congé Maternité', label: 'Congé Maternité' },
    { value: 'Congé Paternité', label: 'Congé Paternité' },
    { value: 'Congé Sans Solde', label: 'Congé Sans Solde' },
    { value: 'Formation', label: 'Formation' },
  ];

  sortOptions = [
    { value: 'newest', label: 'Plus récent' },
    { value: 'oldest', label: 'Plus ancien' },
  ];

  summaryCards = [
    { label: 'Solde Congé Annuel', value: '22 j', color: '#2563EB', bg: 'rgba(37,99,235,0.08)' },
    { label: 'Jours Pris', value: '0 j', color: '#10B981', bg: 'rgba(16,185,129,0.08)' },
    { label: 'En Attente', value: '0 j', color: '#F59E0B', bg: 'rgba(245,158,11,0.08)' },
    { label: 'Restants', value: '22 j', color: '#8B5CF6', bg: 'rgba(139,92,246,0.08)' },
  ];

  constructor(
    private dialog: MatDialog,
    private leaveService: LeaveService,
    private employeeService: EmployeeService,
    private settingsService: SettingsService,
    private notify: NotificationService,
  ) {}

  ngOnInit(): void {
    this.settingsService.get().subscribe({
      next: (res) => this.settings = res.data,
      error: () => {}
    });
    this.loadEmployees();
    this.loadLeaveRequests();
  }

  loadEmployees(): void {
    this.employeeService.getAll('', '', 0, 100).subscribe({
      next: (res) => {
        this.employees = res.data?.content || [];
      }
    });
  }

  loadLeaveRequests(): void {
    this.loading = true;

    const load$ = this.selectedEmployeeId
      ? this.leaveService.getByEmployeeId(this.selectedEmployeeId)
      : this.leaveService.getAll(this.search, this.filterStatus, this.filterType, this.sortBy);

    load$.subscribe({
      next: (res) => {
        let data = res.data || [];

        if (!this.selectedEmployeeId) {
          if (this.search) {
            const q = this.search.toLowerCase();
            data = data.filter(r =>
              `${r.firstName} ${r.lastName}`.toLowerCase().includes(q)
            );
          }
          if (this.filterStatus) {
            data = data.filter(r => r.status === this.filterStatus);
          }
          if (this.filterType) {
            data = data.filter(r => r.leaveType === this.filterType);
          }
        }

        if (this.sortBy === 'newest') {
          data.sort((a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime());
        } else {
          data.sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime());
        }

        this.requests = data;
        this.loading = false;
        this.updateSummaryCards();
      },
      error: () => {
        this.loading = false;
        this.notify.error('Erreur lors du chargement des demandes');
      }
    });

    if (this.selectedEmployeeId) {
      this.leaveService.getBalance(this.selectedEmployeeId).subscribe({
        next: (res) => {
          this.leaveBalance = res.data || [];
          this.updateSummaryCards();
        }
      });
    } else {
      this.leaveBalance = [];
      this.updateSummaryCards();
    }
  }

  onEmployeeFilterChange(): void {
    this.loadLeaveRequests();
  }

  onFilterChange(): void {
    this.loadLeaveRequests();
  }

  clearFilters(): void {
    this.search = '';
    this.filterStatus = '';
    this.filterType = '';
    this.sortBy = 'newest';
    this.selectedEmployeeId = null;
    this.leaveBalance = [];
    this.loadLeaveRequests();
  }

  updateSummaryCards(): void {
    const pendingDays = this.requests
      .filter(r => r.status === 'En cours')
      .reduce((sum, r) => sum + r.days, 0);

    const approvedDays = this.requests
      .filter(r => r.status === 'Approuvé')
      .reduce((sum, r) => sum + r.days, 0);

    if (this.selectedEmployeeId && this.leaveBalance.length > 0) {
      const annualBalance = this.leaveBalance.find(b => b.type === 'Congé Annuel');
      const total = annualBalance?.total ?? 22;
      const used = annualBalance?.used ?? approvedDays;
      const remaining = annualBalance?.remaining ?? Math.max(total - used, 0);

      this.summaryCards = [
        { label: 'Solde Total', value: `${total} j`, color: '#2563EB', bg: 'rgba(37,99,235,0.08)' },
        { label: 'Jours Pris', value: `${used} j`, color: '#10B981', bg: 'rgba(16,185,129,0.08)' },
        { label: 'En Attente', value: `${pendingDays} j`, color: '#F59E0B', bg: 'rgba(245,158,11,0.08)' },
        { label: 'Restants', value: `${remaining} j`, color: '#8B5CF6', bg: 'rgba(139,92,246,0.08)' },
      ];
    } else {
      const totalDemandes = this.requests.length;

      const uniqueEmployees = new Set(this.requests.map(r => r.employeeId)).size;

      this.summaryCards = [
        { label: 'Total Demandes', value: `${totalDemandes}`, color: '#2563EB', bg: 'rgba(37,99,235,0.08)' },
        { label: 'Approuvées', value: `${approvedDays} j`, color: '#10B981', bg: 'rgba(16,185,129,0.08)' },
        { label: 'En Attente', value: `${pendingDays} j`, color: '#F59E0B', bg: 'rgba(245,158,11,0.08)' },
        { label: 'Employés Concernés', value: `${uniqueEmployees}`, color: '#8B5CF6', bg: 'rgba(139,92,246,0.08)' },
      ];
    }
  }

  openNewDemande(): void {
    const dialogRef = this.dialog.open(LeaveFormDialogComponent, {
      width: '640px',
      maxHeight: '90vh',
      panelClass: 'leave-dialog-panel',
      data: {}
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.leaveService.create(result).subscribe({
          next: () => {
            this.notify.success('Demande de congé créée');
            this.loadLeaveRequests();
          },
          error: (err) => this.notify.error(err.error?.message || 'Erreur lors de la création')
        });
      }
    });
  }

  viewDetails(req: LeaveRequest): void {
    this.leaveService.getById(req.id).subscribe({
      next: (res) => {
        this.dialog.open(LeaveDetailDialogComponent, {
          width: '600px',
          maxHeight: '90vh',
          panelClass: 'leave-dialog-panel',
          data: res.data
        });
      }
    });
  }

  editLeave(req: LeaveRequest): void {
    this.leaveService.getById(req.id).subscribe({
      next: (res) => {
        const dialogRef = this.dialog.open(LeaveFormDialogComponent, {
          width: '640px',
          maxHeight: '90vh',
          panelClass: 'leave-dialog-panel',
          data: { editMode: true, leave: res.data }
        });

        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            this.leaveService.update(req.id, result).subscribe({
              next: () => {
                this.notify.success('Demande mise à jour');
                this.loadLeaveRequests();
              },
              error: (err) => this.notify.error(err.error?.message || 'Erreur lors de la mise à jour')
            });
          }
        });
      }
    });
  }

  deleteLeave(req: LeaveRequest): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      panelClass: 'leave-dialog-panel',
      data: {
        title: 'Supprimer la demande',
        message: `Voulez-vous vraiment supprimer la demande de congé de ${req.firstName} ${req.lastName} ? Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        type: 'delete'
      } as ConfirmDialogData
    });
    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.leaveService.delete(req.id).subscribe({
          next: () => {
            this.notify.success('Demande supprimée');
            this.loadLeaveRequests();
          },
          error: () => this.notify.error('Erreur lors de la suppression')
        });
      }
    });
  }

  approve(req: LeaveRequest): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      panelClass: 'leave-dialog-panel',
      data: {
        title: 'Approuver la demande',
        message: `Voulez-vous approuver la demande de congé de ${req.firstName} ${req.lastName} ?`,
        confirmLabel: 'Approuver',
        type: 'approve'
      } as ConfirmDialogData
    });
    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.leaveService.approve(req.id).subscribe({
          next: () => {
            this.notify.success('Demande approuvée');
            this.loadLeaveRequests();
          },
          error: () => this.notify.error('Erreur lors de l\'approbation')
        });
      }
    });
  }

  refuse(req: LeaveRequest): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      panelClass: 'leave-dialog-panel',
      data: {
        title: 'Refuser la demande',
        message: `Voulez-vous refuser la demande de congé de ${req.firstName} ${req.lastName} ?`,
        confirmLabel: 'Refuser',
        type: 'refuse'
      } as ConfirmDialogData
    });
    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.leaveService.refuse(req.id).subscribe({
          next: () => {
            this.notify.success('Demande refusée');
            this.loadLeaveRequests();
          },
          error: () => this.notify.error('Erreur lors du refus')
        });
      }
    });
  }

  resetToPending(req: LeaveRequest): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      panelClass: 'leave-dialog-panel',
      data: {
        title: 'Remettre en attente',
        message: `Voulez-vous remettre la demande de congé de ${req.firstName} ${req.lastName} en attente ? La paie sera recalculée automatiquement.`,
        confirmLabel: 'Confirmer',
        type: 'approve'
      } as ConfirmDialogData
    });
    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.leaveService.resetToPending(req.id).subscribe({
          next: () => {
            this.notify.success('Demande remise en attente');
            this.loadLeaveRequests();
          },
          error: () => this.notify.error('Erreur lors de la modification')
        });
      }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'Approuvé': return 'status-approved';
      case 'En cours': return 'status-pending';
      case 'Refusé': return 'status-refused';
      default: return '';
    }
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'Approuvé': return 'check_circle';
      case 'En cours': return 'schedule';
      case 'Refusé': return 'cancel';
      default: return '';
    }
  }

  barWidth(used: number, total: number | null): number {
    if (!total || total <= 0) return 0;
    return Math.min((used / total) * 100, 100);
  }

  formatDate(d: string): string {
    if (!d) return '';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  exportPdf(): void {
    const rows = this.requests.map(r =>
      `<tr><td>${r.firstName} ${r.lastName}</td><td>${r.leaveType}</td><td>${this.formatDate(r.startDate)}</td><td>${this.formatDate(r.endDate)}</td><td>${r.days} j</td><td>${r.status}</td></tr>`
    ).join('');
    const balanceRows = this.leaveBalance.map(b =>
      `<tr><td>${b.type}</td><td>${b.total !== null ? b.total + ' j' : 'Illimité'}</td><td>${b.used} j</td><td>${b.remaining !== null ? b.remaining + ' j' : 'Illimité'}</td></tr>`
    ).join('');

    const contentHtml = `
      <div class="section-title">Solde de Congés par Type</div>
      <table><thead><tr><th>Type</th><th>Total</th><th>Utilisé</th><th>Restant</th></tr></thead><tbody>${balanceRows}</tbody></table>
      <div class="section-title">Demandes de Congé</div>
      <table><thead><tr><th>Employé</th><th>Type</th><th>Début</th><th>Fin</th><th>Durée</th><th>Statut</th></tr></thead><tbody>${rows}</tbody></table>
    `;

    openPdfWindow('Rapport des Congés', new Date().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }), contentHtml, this.settings as PdfCompanySettings);
  }
}
