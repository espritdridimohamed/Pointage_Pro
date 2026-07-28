import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';
import { LeaveRequest, LeaveRequestCreate, LeaveBalance } from '../models/leave.model';

@Injectable({ providedIn: 'root' })
export class LeaveService {
  private readonly apiUrl = `${environment.apiUrl}/leaves`;

  constructor(private http: HttpClient) {}

  getAll(search?: string, status?: string, leaveType?: string, sort?: string): Observable<ApiResponse<LeaveRequest[]>> {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    if (status) params = params.set('status', status);
    if (leaveType) params = params.set('leaveType', leaveType);
    if (sort) params = params.set('sort', sort);
    return this.http.get<ApiResponse<LeaveRequest[]>>(this.apiUrl, { params });
  }

  getById(id: number): Observable<ApiResponse<LeaveRequest>> {
    return this.http.get<ApiResponse<LeaveRequest>>(`${this.apiUrl}/${id}`);
  }

  getByEmployeeId(employeeId: number): Observable<ApiResponse<LeaveRequest[]>> {
    return this.http.get<ApiResponse<LeaveRequest[]>>(`${this.apiUrl}/employee/${employeeId}`);
  }

  getBalance(employeeId: number): Observable<ApiResponse<LeaveBalance[]>> {
    return this.http.get<ApiResponse<LeaveBalance[]>>(`${this.apiUrl}/balance/${employeeId}`);
  }

  getStats(): Observable<ApiResponse<{ pending: number }>> {
    return this.http.get<ApiResponse<{ pending: number }>>(`${this.apiUrl}/stats`);
  }

  create(request: LeaveRequestCreate): Observable<ApiResponse<LeaveRequest>> {
    return this.http.post<ApiResponse<LeaveRequest>>(this.apiUrl, request);
  }

  update(id: number, request: LeaveRequestCreate): Observable<ApiResponse<LeaveRequest>> {
    return this.http.put<ApiResponse<LeaveRequest>>(`${this.apiUrl}/${id}`, request);
  }

  approve(id: number): Observable<ApiResponse<LeaveRequest>> {
    return this.http.put<ApiResponse<LeaveRequest>>(`${this.apiUrl}/${id}/approve`, {});
  }

  refuse(id: number): Observable<ApiResponse<LeaveRequest>> {
    return this.http.put<ApiResponse<LeaveRequest>>(`${this.apiUrl}/${id}/refuse`, {});
  }

  resetToPending(id: number): Observable<ApiResponse<LeaveRequest>> {
    return this.http.put<ApiResponse<LeaveRequest>>(`${this.apiUrl}/${id}/pending`, {});
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.apiUrl}/${id}`);
  }
}
