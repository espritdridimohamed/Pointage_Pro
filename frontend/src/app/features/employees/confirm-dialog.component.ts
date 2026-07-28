import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="confirm-dialog">
      <div class="icon-wrapper danger">
        <mat-icon>delete_outline</mat-icon>
      </div>
      <h3>Supprimer l'employé</h3>
      <p>
        Êtes-vous sûr de vouloir supprimer
        <strong>{{ data.firstName }} {{ data.lastName }}</strong> ?
        <br>
        <span class="warning">Cette action est irréversible.</span>
      </p>
      <div class="actions">
        <button class="btn-cancel" (click)="onCancel()">Annuler</button>
        <button class="btn-delete" (click)="onConfirm()">
          <mat-icon>delete_outline</mat-icon>
          Supprimer
        </button>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: 'Inter', sans-serif;
    }

    .confirm-dialog {
      padding: 32px 30px 28px;
      text-align: center;
      background: #fff;
      border-radius: 16px;
    }

    .icon-wrapper {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 20px;

      &.danger {
        background: #fef2f2;
        border: 2px solid #fecaca;

        mat-icon {
          font-size: 30px;
          width: 30px;
          height: 30px;
          color: #ef4444;
        }
      }
    }

    h3 {
      font-size: 18px;
      font-weight: 700;
      color: #1e293b;
      margin: 0 0 10px;
    }

    p {
      font-size: 14px;
      color: #64748b;
      margin: 0 0 28px;
      line-height: 1.6;

      strong {
        color: #1e293b;
        font-weight: 600;
      }

      .warning {
        color: #ef4444;
        font-size: 13px;
        font-weight: 500;
      }
    }

    .actions {
      display: flex;
      gap: 12px;
      justify-content: center;
    }

    .btn-cancel {
      height: 42px;
      padding: 0 24px;
      border-radius: 10px;
      border: 1px solid #e2e8f0;
      background: #fff;
      color: #475569;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      font-family: 'Inter', sans-serif;
      transition: all 0.2s;

      &:hover {
        background: #f8fafc;
        border-color: #cbd5e1;
      }
    }

    .btn-delete {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 42px;
      padding: 0 24px;
      border-radius: 10px;
      border: none;
      background: #ef4444;
      color: #fff;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      font-family: 'Inter', sans-serif;
      transition: all 0.2s;
      box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }

      &:hover {
        background: #dc2626;
        box-shadow: 0 6px 16px rgba(239, 68, 68, 0.4);
        transform: translateY(-1px);
      }
    }
  `]
})
export class ConfirmDialogComponent {
  constructor(
    private dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { firstName: string; lastName: string }
  ) {}

  onConfirm(): void {
    this.dialogRef.close(true);
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }
}
