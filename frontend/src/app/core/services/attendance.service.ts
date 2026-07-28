import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';
import { AttendanceRecord, AttendanceSummary } from '../models/attendance.model';

@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private readonly apiUrl = `${environment.apiUrl}/attendance`;

  constructor(private http: HttpClient) {}

  getByMonth(month: number, year: number, employeeId?: number): Observable<ApiResponse<AttendanceRecord[]>> {
    let params = new HttpParams().set('month', month).set('year', year);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<ApiResponse<AttendanceRecord[]>>(this.apiUrl, { params });
  }

  getSummary(employeeId: number, month: number, year: number): Observable<ApiResponse<AttendanceSummary>> {
    const params = new HttpParams().set('employeeId', employeeId).set('month', month).set('year', year);
    return this.http.get<ApiResponse<AttendanceSummary>>(`${this.apiUrl}/summary`, { params });
  }
}
