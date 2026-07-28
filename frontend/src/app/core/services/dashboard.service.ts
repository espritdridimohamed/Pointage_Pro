import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';

export interface DashboardStats {
  totalEmployees: number;
  presentToday: number;
  absentToday: number;
  lateToday: number;
  pendingLeaves: number;
}

export interface DashboardChart {
  labels: string[];
  present: number[];
  absent: number[];
  late: number[];
  weekLabel: string;
  totalEmployees: number;
}

export interface RecentAttendance {
  employeeId: number;
  firstName: string;
  lastName: string;
  position: string;
  photo: string;
  initials: string;
  avatarColor: string;
  checkIn: string;
  checkOut: string;
  workedHours: number;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly apiUrl = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  getStats(): Observable<ApiResponse<DashboardStats>> {
    return this.http.get<ApiResponse<DashboardStats>>(`${this.apiUrl}/stats`);
  }

  getChart(): Observable<ApiResponse<DashboardChart>> {
    return this.http.get<ApiResponse<DashboardChart>>(`${this.apiUrl}/chart`);
  }

  getRecent(): Observable<ApiResponse<RecentAttendance[]>> {
    return this.http.get<ApiResponse<RecentAttendance[]>>(`${this.apiUrl}/recent`);
  }
}
