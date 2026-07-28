import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';

export interface UserProfile {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone: string;
  role: string;
}

export interface SessionResponse {
  id: number;
  deviceInfo: string;
  ipAddress: string;
  createdAt: string;
  lastAccessedAt: string;
  current: boolean;
}

export interface LoginHistoryResponse {
  id: number;
  ipAddress: string;
  userAgent: string;
  status: string;
  attemptedAt: string;
}

export interface NotificationPrefs {
  emailNotifications: boolean;
  browserNotifications: boolean;
  dailySummary: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  getProfile(): Observable<ApiResponse<UserProfile>> {
    return this.http.get<ApiResponse<UserProfile>>(`${this.apiUrl}/me`);
  }

  updateProfile(data: { fullName: string; email: string; phone: string }): Observable<ApiResponse<UserProfile>> {
    return this.http.put<ApiResponse<UserProfile>>(`${this.apiUrl}/me`, data);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/change-password`, {
      currentPassword,
      newPassword
    });
  }

  verifyLogin2FA(tempToken: string, code: string): Observable<ApiResponse<{ token: string; type: string; username: string; fullName: string; email: string; role: string }>> {
    return this.http.post<ApiResponse<{ token: string; type: string; username: string; fullName: string; email: string; role: string }>>(`${this.apiUrl}/login/2fa`, { tempToken, code });
  }

  setup2FA(): Observable<ApiResponse<{ secret: string; otpauthUri: string }>> {
    return this.http.post<ApiResponse<{ secret: string; otpauthUri: string }>>(`${this.apiUrl}/2fa/setup`, {});
  }

  verify2FA(code: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/2fa/verify`, { code });
  }

  enable2FA(): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/2fa/enable`, {});
  }

  disable2FA(password: string, code: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/2fa/disable`, { password, code });
  }

  getSessions(): Observable<ApiResponse<SessionResponse[]>> {
    return this.http.get<ApiResponse<SessionResponse[]>>(`${this.apiUrl}/sessions`);
  }

  revokeSession(id: number): Observable<ApiResponse<string>> {
    return this.http.delete<ApiResponse<string>>(`${this.apiUrl}/sessions/${id}`);
  }

  getLoginHistory(): Observable<ApiResponse<LoginHistoryResponse[]>> {
    return this.http.get<ApiResponse<LoginHistoryResponse[]>>(`${this.apiUrl}/login-history`);
  }

  getNotificationPrefs(): Observable<ApiResponse<NotificationPrefs>> {
    return this.http.get<ApiResponse<NotificationPrefs>>(`${this.apiUrl}/preferences`);
  }

  updateNotificationPrefs(prefs: NotificationPrefs): Observable<ApiResponse<string>> {
    return this.http.put<ApiResponse<string>>(`${this.apiUrl}/preferences`, prefs);
  }

  forgotPassword(email: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/forgot-password`, { email });
  }

  verifyResetCode(email: string, code: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/verify-reset-code`, { email, code });
  }

  resetPassword(email: string, code: string, newPassword: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/reset-password`, { email, code, newPassword });
  }

  revokeAllSessions(): Observable<ApiResponse<string>> {
    return this.http.delete<ApiResponse<string>>(`${this.apiUrl}/sessions`);
  }
}
