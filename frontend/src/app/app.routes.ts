import { Routes } from '@angular/router';
import { authGuard, loginGuard } from './core/auth/auth.guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [loginGuard],
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./pages/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    component: MainLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        data: { animation: 'dashboard' },
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'employees',
        data: { animation: 'employees' },
        loadComponent: () => import('./features/employees/employee-list.component').then(m => m.EmployeeListComponent)
      },
      {
        path: 'attendance',
        data: { animation: 'attendance' },
        loadComponent: () => import('./features/attendance/attendance-list.component').then(m => m.AttendanceListComponent)
      },
      {
        path: 'leaves',
        data: { animation: 'leaves' },
        loadComponent: () => import('./features/leaves/leave-list.component').then(m => m.LeaveListComponent)
      },
      {
        path: 'payroll',
        data: { animation: 'payroll' },
        loadComponent: () => import('./features/payroll/payroll-list.component').then(m => m.PayrollListComponent)
      },
      {
        path: 'reports',
        data: { animation: 'reports' },
        loadComponent: () => import('./features/reports/report-view.component').then(m => m.ReportViewComponent)
      },
      {
        path: 'settings',
        data: { animation: 'settings' },
        loadComponent: () => import('./features/settings/settings-view.component').then(m => m.SettingsViewComponent)
      },
      {
        path: 'profile',
        data: { animation: 'profile' },
        loadComponent: () => import('./features/profile/profile-view.component').then(m => m.ProfileViewComponent)
      },
    ]
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];
