import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  type: 'approve' | 'refuse' | 'delete';
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatIconModule, MatButtonModule],
  template: `
    <div class="confirm-dialog">
      <div class="dialog-header">
        <button class="close-btn" (click)="close()">
          <mat-icon>close</mat-icon>
        </button>
      </div>
      <div class="dialog-body">
        <div class="icon-container" [ngClass]="'icon-' + data.type">
          <mat-icon>{{ getIcon() }}</mat-icon>
        </div>
        <h3>{{ data.title }}</h3>
        <p>{{ data.message }}</p>
      </div>
      <div class="dialog-footer">
        <button class="btn-cancel" (click)="close()">Annuler</button>
        <button class="btn-confirm" [ngClass]="'btn-' + data.type" (click)="confirm()">{{ data.confirmLabel }}</button>
      </div>
    </div>
  `,
  styles: [`
    .confirm-dialog { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #fff; border-radius: 20px; overflow: hidden; }
    .dialog-header { display: flex; justify-content: flex-end; padding: 12px 16px 0; }
    .close-btn { width: 28px; height: 28px; border-radius: 8px; border: none; background: transparent; color: #94A3B8; cursor: pointer; display: flex; align-items: center; justify-content: center; }
    .close-btn:hover { background: #F1F5F9; color: #64748B; }
    .close-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .dialog-body { padding: 0 32px 24px; text-align: center; }
    .icon-container { width: 56px; height: 56px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; }
    .icon-container mat-icon { font-size: 28px; width: 28px; height: 28px; }
    .icon-approve { background: rgba(16, 185, 129, 0.1); color: #059669; }
    .icon-refuse { background: rgba(245, 158, 11, 0.1); color: #D97706; }
    .icon-delete { background: rgba(239, 68, 68, 0.1); color: #DC2626; }
    .dialog-body h3 { font-size: 17px; font-weight: 700; color: #0F172A; margin: 0 0 8px; }
    .dialog-body p { font-size: 14px; color: #64748B; margin: 0; line-height: 1.5; }
    .dialog-footer { display: flex; gap: 10px; padding: 16px 32px; border-top: 1px solid #F1F5F9; background: #F8FAFC; border-radius: 0 0 20px 20px; justify-content: center; }
    .btn-cancel { padding: 10px 20px; border-radius: 10px; border: 1px solid #E2E8F0; background: #fff; color: #374151; font-size: 13px; font-weight: 600; cursor: pointer; font-family: 'Inter', sans-serif; transition: background 0.15s; }
    .btn-cancel:hover { background: #F1F5F9; }
    .btn-confirm { padding: 10px 20px; border-radius: 10px; border: none; font-size: 13px; font-weight: 600; cursor: pointer; font-family: 'Inter', sans-serif; color: #fff; transition: opacity 0.15s; }
    .btn-confirm:hover { opacity: 0.9; }
    .btn-approve { background: #059669; }
    .btn-refuse { background: #D97706; }
    .btn-delete { background: #DC2626; }
  `]
})
export class ConfirmDialogComponent {
  constructor(
    private dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {}

  getIcon(): string {
    switch (this.data.type) {
      case 'approve': return 'check_circle';
      case 'refuse': return 'cancel';
      case 'delete': return 'delete';
    }
  }

  close(): void { this.dialogRef.close(false); }
  confirm(): void { this.dialogRef.close(true); }
}
