import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EmployeeRequest, Employee } from '../../core/models/employee.model';
import { EmployeeService } from '../../core/services/employee.service';

@Component({
  selector: 'app-employee-form-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule,
    MatIconModule, MatProgressSpinnerModule
  ],
  templateUrl: './employee-form-dialog.component.html',
  styleUrl: './employee-form-dialog.component.scss'
})
export class EmployeeFormDialogComponent implements OnInit {
  form!: FormGroup;
  isEdit = false;
  totalSteps = 6;
  currentStep = 1;
  loading = false;
  errorMessage = '';
  photoPreview: string | null = null;
  stepNames = ['Personnelles', 'Professionnelles', 'Horaires', 'Salaire', 'Congés', 'Récapitulatif'];

  departments = ['Finance', 'IT', 'RH', 'Ventes', 'Maintenance', 'Design'];
  contractTypes = ['CDI', 'CDD', 'Stage', 'Freelance', 'Intérim'];

  weekDays = [
    { key: 'LUN', label: 'Lundi' },
    { key: 'MAR', label: 'Mardi' },
    { key: 'MER', label: 'Mercredi' },
    { key: 'JEU', label: 'Jeudi' },
    { key: 'VEN', label: 'Vendredi' },
    { key: 'SAM', label: 'Samedi' },
    { key: 'DIM', label: 'Dimanche' },
  ];

  scheduleEnabled: Record<string, boolean> = {
    LUN: true, MAR: true, MER: true, JEU: true, VEN: true, SAM: true, DIM: false,
  };
  scheduleStart: Record<string, string> = {
    LUN: '08:00', MAR: '08:00', MER: '08:00', JEU: '08:00', VEN: '08:00', SAM: '08:00', DIM: '08:00',
  };
  scheduleEnd: Record<string, string> = {
    LUN: '17:00', MAR: '17:00', MER: '17:00', JEU: '17:00', VEN: '17:00', SAM: '17:00', DIM: '17:00',
  };

  constructor(
    private fb: FormBuilder,
    private employeeService: EmployeeService,
    private dialogRef: MatDialogRef<EmployeeFormDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { mode: 'create' | 'edit'; employee?: Employee }
  ) {
    this.isEdit = data.mode === 'edit';
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      firstName: ['', [Validators.required, Validators.maxLength(50)]],
      lastName: ['', [Validators.required, Validators.maxLength(50)]],
      email: ['', [Validators.email, Validators.maxLength(100)]],
      phone: ['', Validators.maxLength(20)],
      birthDate: [''],
      cin: ['', Validators.maxLength(20)],
      address: ['', Validators.maxLength(255)],
      photo: [''],
      position: ['', Validators.maxLength(100)],
      department: [''],
      hiringDate: [''],
      contractType: [''],
      baseSalary: [null, [Validators.min(0)]],
      primeTransport: [0, [Validators.min(0)]],
      primePerformance: [0, [Validators.min(0)]],
      primeOther: [0, [Validators.min(0)]],
      status: ['ACTIF'],
      matricule: [''],
      rfidUid: [''],
      annualLeaveDays: [null],
      maternityLeaveDays: [null],
      paternityLeaveDays: [null],
    });

    if (this.isEdit && this.data.employee) {
      const emp = this.data.employee;
      this.form.patchValue({
        firstName: emp.firstName,
        lastName: emp.lastName,
        email: emp.email || '',
        phone: emp.phone || '',
        birthDate: emp.birthDate ? emp.birthDate.substring(0, 10) : '',
        cin: emp.cin || '',
        address: emp.address || '',
        photo: emp.photo || '',
        position: emp.position || '',
        department: emp.department || '',
        hiringDate: emp.hiringDate ? emp.hiringDate.substring(0, 10) : '',
        contractType: emp.contractType || '',
        baseSalary: emp.baseSalary,
        primeTransport: emp.primeTransport || 0,
        primePerformance: emp.primePerformance || 0,
        primeOther: emp.primeOther || 0,
        status: emp.status,
        matricule: emp.matricule || '',
        rfidUid: emp.rfidUid || '',
        annualLeaveDays: emp.annualLeaveDays ?? null,
        maternityLeaveDays: emp.maternityLeaveDays ?? null,
        paternityLeaveDays: emp.paternityLeaveDays ?? null,
      });
      if (emp.photo) {
        this.photoPreview = emp.photo;
      }
      if (emp.weeklySchedule) {
        this.loadSchedule(emp.weeklySchedule);
      }
    }
  }

  loadSchedule(json: string): void {
    try {
      const schedule = JSON.parse(json);
      for (const day of Object.keys(schedule)) {
        if (this.scheduleEnabled.hasOwnProperty(day)) {
          this.scheduleEnabled[day] = true;
          this.scheduleStart[day] = schedule[day].start || '08:00';
          this.scheduleEnd[day] = schedule[day].end || '17:00';
        }
      }
    } catch {}
  }

  buildScheduleJson(): string | null {
    const result: Record<string, { start: string; end: string }> = {};
    let hasEnabled = false;
    for (const day of this.weekDays) {
      if (this.scheduleEnabled[day.key]) {
        hasEnabled = true;
        result[day.key] = { start: this.scheduleStart[day.key] || '08:00', end: this.scheduleEnd[day.key] || '17:00' };
      }
    }
    return hasEnabled ? JSON.stringify(result) : null;
  }

  nextStep(): void {
    if (this.currentStep < this.totalSteps) {
      this.currentStep++;
    }
  }

  prevStep(): void {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }

  goToStep(step: number): void {
    if (step <= this.currentStep) {
      this.currentStep = step;
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    const formValue = this.form.value;
    const request: EmployeeRequest = {
      firstName: formValue.firstName,
      lastName: formValue.lastName,
      email: formValue.email || undefined,
      phone: formValue.phone || undefined,
      birthDate: formValue.birthDate || undefined,
      cin: formValue.cin || undefined,
      address: formValue.address || undefined,
      photo: formValue.photo || undefined,
      position: formValue.position || undefined,
      department: formValue.department || undefined,
      hiringDate: formValue.hiringDate || undefined,
      contractType: formValue.contractType || undefined,
      baseSalary: formValue.baseSalary || undefined,
      primeTransport: formValue.primeTransport || 0,
      primePerformance: formValue.primePerformance || 0,
      primeOther: formValue.primeOther || 0,
      status: formValue.status || 'ACTIF',
      matricule: formValue.matricule || undefined,
      rfidUid: formValue.rfidUid || undefined,
      weeklySchedule: this.buildScheduleJson() || undefined,
      annualLeaveDays: formValue.annualLeaveDays != null ? formValue.annualLeaveDays : undefined,
      maternityLeaveDays: formValue.maternityLeaveDays != null ? formValue.maternityLeaveDays : undefined,
      paternityLeaveDays: formValue.paternityLeaveDays != null ? formValue.paternityLeaveDays : undefined,
    };

    const operation = this.isEdit
      ? this.employeeService.update(this.data.employee!.id, request)
      : this.employeeService.create(request);

    operation.subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'An error occurred';
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      if (file.size > 2 * 1024 * 1024) {
        this.errorMessage = 'La photo ne doit pas dépasser 2 Mo';
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        const img = new Image();
        img.onload = () => {
          const canvas = document.createElement('canvas');
          const maxSize = 200;
          let w = img.width;
          let h = img.height;
          if (w > h && w > maxSize) { h = (h * maxSize) / w; w = maxSize; }
          else if (h > maxSize) { w = (w * maxSize) / h; h = maxSize; }
          canvas.width = w;
          canvas.height = h;
          canvas.getContext('2d')!.drawImage(img, 0, 0, w, h);
          const resized = canvas.toDataURL('image/jpeg', 0.6);
          this.photoPreview = resized;
          this.form.patchValue({ photo: resized });
        };
        img.src = reader.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  getFullName(): string {
    const f = this.form.value;
    return `${f.firstName || '—'} ${f.lastName || '—'}`;
  }

  getScheduleSummary(): string {
    const enabled = this.weekDays.filter(d => this.scheduleEnabled[d.key]);
    if (enabled.length === 0) return 'Horaire global par défaut';
    if (enabled.length === 7 && this.scheduleStart['LUN'] === this.scheduleStart['MAR'] &&
        this.scheduleStart['LUN'] === this.scheduleStart['MER'] &&
        this.scheduleStart['LUN'] === this.scheduleEnd['LUN']) {
      return `${this.scheduleStart['LUN']} → ${this.scheduleEnd['LUN']}`;
    }
    return `${enabled.length} jour(s) configuré(s)`;
  }

  getStepTitle(): string {
    const titles = ['', 'Informations Personnelles', 'Informations Professionnelles', 'Horaires de Travail', 'Salaire & Primes', 'Droits de Congés', 'Récapitulatif'];
    return titles[this.currentStep] || '';
  }
}
