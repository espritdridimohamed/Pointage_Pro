import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';

export interface AbsenceBreakdown {
  name: string;
  value: number;
  color: string;
}

export interface EmployeeAttendanceStats {
  employeeId: number;
  firstName: string;
  lastName: string;
  department: string;
  daysPresent: number;
  daysLate: number;
  daysAbsent: number;
  overtimeHours: number;
}

export interface ReportData {
  labels: string[];
  presence: number[];
  retards: number[];
  masse: number[];
  overtimeHours: number[];
  totalEmployees: number;
  absences: AbsenceBreakdown[];
  employeeStats: EmployeeAttendanceStats[];
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly apiUrl = `${environment.apiUrl}/reports`;

  constructor(private http: HttpClient) {}

  getMonthly(month: number, year: number): Observable<ApiResponse<ReportData>> {
    const params = new HttpParams().set('month', month).set('year', year);
    return this.http.get<ApiResponse<ReportData>>(`${this.apiUrl}/monthly`, { params });
  }

  getAnnual(year: number): Observable<ApiResponse<ReportData>> {
    const params = new HttpParams().set('year', year);
    return this.http.get<ApiResponse<ReportData>>(`${this.apiUrl}/annual`, { params });
  }
}
