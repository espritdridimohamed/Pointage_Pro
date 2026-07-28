import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { LeaveRequest } from '../../core/models/leave.model';

@Component({
  selector: 'app-leave-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatIconModule, MatButtonModule],
  template: `
    <div class="leave-dialog">
      <div class="dialog-header">
        <div class="header-left">
          <div class="header-icon">
            <mat-icon>info</mat-icon>
          </div>
          <div class="header-text">
            <h2>Détails de la Demande</h2>
            <p>{{ data.firstName }} {{ data.lastName }}</p>
          </div>
        </div>
        <button class="close-btn" (click)="close()">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      <div class="dialog-body">
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">Employé</span>
            <div class="employee-cell">
              <div class="avatar" [style.background]="data.avatarColor">{{ data.initials }}</div>
              <span>{{ data.firstName }} {{ data.lastName }}</span>
            </div>
          </div>
          <div class="detail-item">
            <span class="detail-label">Type de congé</span>
            <span class="detail-value">{{ data.leaveType }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Date de début</span>
            <span class="detail-value">{{ formatDate(data.startDate) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Date de fin</span>
            <span class="detail-value">{{ formatDate(data.endDate) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Durée</span>
            <span class="detail-value"><strong>{{ data.days }}</strong> jour(s)</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Statut</span>
            <span class="status-badge" [ngClass]="getStatusClass(data.status)">
              <mat-icon>{{ getStatusIcon(data.status) }}</mat-icon>
              {{ data.status }}
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Demandé le</span>
            <span class="detail-value">{{ formatDate(data.requestedDate) }}</span>
          </div>
          @if (data.approvedByName) {
            <div class="detail-item">
              <span class="detail-label">Approuvé par</span>
              <span class="detail-value">{{ data.approvedByName }}</span>
            </div>
          }
        </div>

        @if (data.reason) {
          <div class="detail-section">
            <span class="detail-label">Motif</span>
            <p class="detail-reason">{{ data.reason }}</p>
          </div>
        }

        @if (data.hasAttachment && data.attachment) {
          <div class="detail-section">
            <span class="detail-label">Pièce jointe</span>
            <div class="attachment-preview">
              @if (isImage()) {
                <img [src]="data.attachment" alt="Pièce jointe" class="attachment-image" />
              } @else {
                <div class="attachment-file">
                  <mat-icon>description</mat-icon>
                  <span>Cliquez pour ouvrir le document</span>
                </div>
              }
              <button class="btn-download" (click)="openAttachment()">
                <mat-icon>open_in_new</mat-icon>
                Ouvrir
              </button>
            </div>
          </div>
        }
      </div>

      <div class="dialog-footer">
        <button class="btn-cancel" (click)="close()">Fermer</button>
      </div>
    </div>
  `,
  styles: [`
    .leave-dialog { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
    .dialog-header { display: flex; align-items: center; justify-content: space-between; background: linear-gradient(135deg, #0F172A 0%, #1e293b 100%); padding: 24px 32px; border-radius: 24px 24px 0 0; }
    .header-left { display: flex; align-items: center; gap: 14px; }
    .header-icon { width: 40px; height: 40px; border-radius: 12px; background: rgba(37, 99, 235, 0.3); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .header-icon mat-icon { color: #60A5FA; font-size: 20px; width: 20px; height: 20px; }
    .header-text h2 { font-size: 18px; font-weight: 700; color: #fff; margin: 0; }
    .header-text p { font-size: 12px; color: #94A3B8; margin: 2px 0 0; }
    .close-btn { width: 32px; height: 32px; border-radius: 10px; border: none; background: transparent; color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; }
    .close-btn:hover { background: rgba(255, 255, 255, 0.1); }
    .close-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .dialog-body { padding: 24px 32px; max-height: 500px; overflow-y: auto; background: #fff; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    .detail-item { display: flex; flex-direction: column; gap: 6px; }
    .detail-label { font-size: 12px; font-weight: 600; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px; }
    .detail-value { font-size: 14px; color: #1E293B; font-weight: 500; }
    .employee-cell { display: flex; align-items: center; gap: 10px; }
    .avatar { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 12px; font-weight: 700; flex-shrink: 0; }
    .status-badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: 600; }
    .status-badge mat-icon { font-size: 14px; width: 14px; height: 14px; }
    .status-approved { background: rgba(16, 185, 129, 0.1); color: #059669; }
    .status-pending { background: rgba(217, 119, 6, 0.1); color: #D97706; }
    .status-refused { background: rgba(220, 38, 38, 0.1); color: #DC2626; }
    .detail-section { margin-top: 20px; }
    .detail-reason { font-size: 14px; color: #475569; line-height: 1.6; margin: 6px 0 0; padding: 12px; background: #F8FAFC; border-radius: 8px; }
    .attachment-preview { margin-top: 8px; display: flex; flex-direction: column; gap: 10px; }
    .attachment-image { max-width: 100%; max-height: 300px; border-radius: 8px; border: 1px solid #E2E8F0; object-fit: contain; }
    .attachment-file { display: flex; align-items: center; gap: 10px; padding: 16px; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; }
    .attachment-file mat-icon { color: #2563EB; }
    .attachment-file span { font-size: 13px; color: #64748B; }
    .btn-download { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 8px; border: 1px solid #E2E8F0; background: #fff; color: #334155; font-size: 13px; font-weight: 600; cursor: pointer; font-family: 'Inter', sans-serif; }
    .btn-download:hover { background: #F1F5F9; }
    .btn-download mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .dialog-footer { display: flex; justify-content: flex-end; padding: 16px 32px; border-top: 1px solid #F1F5F9; background: #F8FAFC; border-radius: 0 0 24px 24px; }
    .btn-cancel { padding: 10px 20px; border-radius: 12px; border: 1px solid #E2E8F0; background: #fff; color: #374151; font-size: 14px; font-weight: 600; cursor: pointer; font-family: 'Inter', sans-serif; }
    .btn-cancel:hover { background: #F1F5F9; }
  `]
})
export class LeaveDetailDialogComponent {
  constructor(
    private dialogRef: MatDialogRef<LeaveDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: LeaveRequest
  ) {}

  isImage(): boolean {
    return this.data.attachment?.startsWith('data:image') ?? false;
  }

  openAttachment(): void {
    if (this.data.attachment) {
      const win = window.open('', '_blank');
      if (win) {
        if (this.isImage()) {
          win.document.write(`<html><head><title>Pièce jointe</title></head><body style="display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0;background:#f5f5f5"><img src="${this.data.attachment}" style="max-width:90%;max-height:90vh;border-radius:8px;box-shadow:0 4px 20px rgba(0,0,0,0.15)"/></body></html>`);
        } else {
          win.document.write(`<html><head><title>Pièce jointe</title></head><body style="margin:0"><iframe src="${this.data.attachment}" style="width:100%;height:100vh;border:none"></iframe></body></html>`);
        }
      }
    }
  }

  close(): void { this.dialogRef.close(); }

  formatDate(d: string): string {
    if (!d) return '';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
  }

  getStatusClass(status: string): string {
    switch (status) { case 'Approuvé': return 'status-approved'; case 'En cours': return 'status-pending'; case 'Refusé': return 'status-refused'; default: return ''; }
  }

  getStatusIcon(status: string): string {
    switch (status) { case 'Approuvé': return 'check_circle'; case 'En cours': return 'schedule'; case 'Refusé': return 'cancel'; default: return ''; }
  }
}
