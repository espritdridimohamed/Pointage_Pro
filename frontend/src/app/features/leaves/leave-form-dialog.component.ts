import { Component, OnInit, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { EmployeeService } from '../../core/services/employee.service';
import { LeaveService } from '../../core/services/leave.service';
import { Employee } from '../../core/models/employee.model';
import { LeaveBalance } from '../../core/models/leave.model';

interface LeaveType {
  value: string;
  label: string;
  color: string;
}

@Component({
  selector: 'app-leave-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatIconModule, MatButtonModule],
  templateUrl: './leave-form-dialog.component.html',
  styleUrl: './leave-form-dialog.component.scss'
})
export class LeaveFormDialogComponent implements OnInit {
  submitted = false;
  editMode = false;

  form = {
    employeeId: null as number | null,
    type: '',
    from: '',
    to: '',
    reason: '',
    urgent: false,
  };

  attachmentBase64: string | null = null;
  attachmentName: string = '';
  attachmentTooLarge = false;

  employees: Employee[] = [];
  employeeBalance: LeaveBalance[] = [];

  leaveTypes: LeaveType[] = [
    { value: 'Congé Annuel', label: 'Congé Annuel', color: '#2563EB' },
    { value: 'Congé Maladie', label: 'Congé Maladie', color: '#10B981' },
    { value: 'Congé Maternité', label: 'Congé Maternité', color: '#8B5CF6' },
    { value: 'Congé Paternité', label: 'Congé Paternité', color: '#06B6D4' },
    { value: 'Congé Sans Solde', label: 'Congé Sans Solde', color: '#F59E0B' },
    { value: 'Formation', label: 'Formation', color: '#EC4899' },
  ];

  constructor(
    private dialogRef: MatDialogRef<LeaveFormDialogComponent>,
    private employeeService: EmployeeService,
    private leaveService: LeaveService,
    @Inject(MAT_DIALOG_DATA) public data: any
  ) {}

  ngOnInit(): void {
    this.editMode = this.data?.editMode === true;

    this.employeeService.getAll(undefined, undefined, 0, 100).subscribe({
      next: (res) => {
        this.employees = res.data?.content || [];

        if (this.editMode && this.data?.leave) {
          const leave = this.data.leave;
          this.form.employeeId = leave.employeeId;
          this.form.type = leave.leaveType;
          this.form.from = leave.startDate;
          this.form.to = leave.endDate;
          this.form.reason = leave.reason || '';
          if (leave.hasAttachment && leave.attachment) {
            this.attachmentBase64 = leave.attachment;
            this.attachmentName = 'Document joint';
          }
          this.loadBalance(leave.employeeId);
        }
      }
    });
  }

  onEmployeeChange(): void {
    if (this.form.employeeId) {
      this.loadBalance(this.form.employeeId);
      this.form.type = '';
    } else {
      this.employeeBalance = [];
    }
  }

  private loadBalance(employeeId: number): void {
    this.leaveService.getBalance(employeeId).subscribe({
      next: (res) => {
        this.employeeBalance = res.data || [];
      }
    });
  }

  getRemaining(typeValue: string): number | null {
    const b = this.employeeBalance.find(x => x.type === typeValue);
    if (!b) return null;
    return b.remaining;
  }

  get duration(): number {
    if (!this.form.from || !this.form.to) return 0;
    const d1 = new Date(this.form.from);
    const d2 = new Date(this.form.to);
    const diff = Math.ceil((d2.getTime() - d1.getTime()) / 86400000) + 1;
    return diff > 0 ? diff : 0;
  }

  get isValid(): boolean {
    return !!(this.form.employeeId && this.form.type && this.form.from && this.form.to);
  }

  selectType(type: LeaveType): void {
    this.form.type = this.form.type === type.value ? '' : type.value;
  }

  toggleUrgent(): void {
    this.form.urgent = !this.form.urgent;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    this.attachmentTooLarge = false;

    if (file.size > 5 * 1024 * 1024) {
      this.attachmentTooLarge = true;
      return;
    }

    this.attachmentName = file.name;
    const reader = new FileReader();
    reader.onload = () => {
      this.attachmentBase64 = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  triggerFileInput(): void {
    const fileInput = document.getElementById('attachmentFile') as HTMLInputElement;
    if (fileInput) fileInput.click();
  }

  removeAttachment(): void {
    this.attachmentBase64 = null;
    this.attachmentName = '';
    this.attachmentTooLarge = false;
    const fileInput = document.getElementById('attachmentFile') as HTMLInputElement;
    if (fileInput) fileInput.value = '';
  }

  submit(): void {
    if (!this.isValid) return;
    this.submitted = true;
  }

  close(): void {
    this.dialogRef.close();
  }

  closeAfterSubmit(): void {
    this.dialogRef.close({
      employeeId: this.form.employeeId,
      leaveType: this.form.type,
      startDate: this.form.from,
      endDate: this.form.to,
      reason: this.form.reason,
      attachment: this.attachmentBase64 || undefined,
    });
  }

  getSelectedEmployeeName(): string {
    const emp = this.employees.find(e => e.id === this.form.employeeId);
    return emp ? `${emp.firstName} ${emp.lastName}` : '';
  }

  getSelectedTypeName(): string {
    const t = this.leaveTypes.find(l => l.value === this.form.type);
    return t ? t.label : '';
  }

  formatDate(d: string): string {
    if (!d) return '';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
