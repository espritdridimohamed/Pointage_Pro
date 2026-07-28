import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Employee } from '../../core/models/employee.model';
import { EmployeeService } from '../../core/services/employee.service';
import { NotificationService } from '../../core/services/notification.service';
import { EmployeeFormDialogComponent } from './employee-form-dialog.component';
import { EmployeeDetailDialogComponent } from './employee-detail-dialog.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatDialogModule],
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss'
})
export class EmployeeListComponent implements OnInit {
  selectedDepartment = '';
  searchValue = '';
  hoveredCard: number | null = null;
  loading = false;

  departments: string[] = [];

  employees: Employee[] = [];
  filteredEmployees: Employee[] = [];

  constructor(
    private dialog: MatDialog,
    private employeeService: EmployeeService,
    private notify: NotificationService
  ) {}

  ngOnInit(): void {
    this.loadEmployees();
  }

  loadEmployees(): void {
    this.loading = true;
    this.employeeService.getAll(undefined, undefined, 0, 200).subscribe({
      next: (res) => {
        this.employees = res.data.content;
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notify.error('Erreur lors du chargement des employés');
      }
    });
    this.employeeService.getDepartments().subscribe({
      next: (res) => {
        this.departments = res.data;
      }
    });
  }

  get totalCount(): number {
    return this.filteredEmployees.length;
  }

  onSearch(event: Event): void {
    this.searchValue = (event.target as HTMLInputElement).value.toLowerCase();
    this.applyFilters();
  }

  filterByDepartment(dept: string): void {
    this.selectedDepartment = dept;
    this.applyFilters();
  }

  applyFilters(): void {
    this.filteredEmployees = this.employees.filter(emp => {
      const matchesSearch = !this.searchValue ||
        emp.firstName.toLowerCase().includes(this.searchValue) ||
        emp.lastName.toLowerCase().includes(this.searchValue) ||
        emp.email?.toLowerCase().includes(this.searchValue) ||
        emp.position?.toLowerCase().includes(this.searchValue) ||
        emp.matricule?.toLowerCase().includes(this.searchValue);

      const matchesDept = !this.selectedDepartment ||
        emp.department === this.selectedDepartment;

      return matchesSearch && matchesDept;
    });
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(EmployeeFormDialogComponent, {
      width: '680px',
      maxHeight: '90vh',
      panelClass: 'employee-dialog',
      backdropClass: 'dialog-blur-backdrop',
      data: { mode: 'create' }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadEmployees();
      }
    });
  }

  openEditDialog(employee: Employee): void {
    const dialogRef = this.dialog.open(EmployeeFormDialogComponent, {
      width: '680px',
      maxHeight: '90vh',
      panelClass: 'employee-dialog',
      backdropClass: 'dialog-blur-backdrop',
      data: { mode: 'edit', employee }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadEmployees();
      }
    });
  }

  deleteEmployee(employee: Employee): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      panelClass: 'confirm-dialog-panel',
      backdropClass: 'dialog-blur-backdrop',
      data: { firstName: employee.firstName, lastName: employee.lastName }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.employeeService.delete(employee.id).subscribe({
          next: () => {
            this.notify.success('Employé supprimé avec succès');
            this.loadEmployees();
          },
          error: () => this.notify.error('Erreur lors de la suppression')
        });
      }
    });
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIF': return 'Actif';
      case 'CONGE': return 'Congé';
      case 'INACTIF': return 'Inactif';
      default: return status;
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIF': return 'status-active';
      case 'CONGE': return 'status-conge';
      case 'INACTIF': return 'status-inactif';
      default: return '';
    }
  }

  getDepartmentColor(dept: string): string {
    const colors: Record<string, string> = {
      'Finance': '#2563eb',
      'IT': '#8b5cf6',
      'RH': '#f59e0b',
      'Ventes': '#22c55e',
      'Maintenance': '#6b7280',
      'Design': '#ec4899'
    };
    return colors[dept] || '#64748b';
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

  onCardHover(id: number | null): void {
    this.hoveredCard = id;
  }

  openDetailDialog(employee: Employee): void {
    this.dialog.open(EmployeeDetailDialogComponent, {
      width: '960px',
      maxHeight: '90vh',
      panelClass: 'employee-dialog',
      backdropClass: 'dialog-blur-backdrop',
      data: employee
    });
  }
}
