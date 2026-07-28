import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { LoginRequest, AuthResponse } from '../models/user.model';
import { AuthService as AuthApiService } from '../services/auth-api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'pointagepro_token';
  private readonly USER_KEY = 'pointagepro_user';
  private currentUserSubject = new BehaviorSubject<AuthResponse | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  private twoFactorRequiredSubject = new BehaviorSubject<boolean>(false);
  twoFactorRequired$ = this.twoFactorRequiredSubject.asObservable();

  private tempTokenSubject = new BehaviorSubject<string | null>(null);
  tempToken$ = this.tempTokenSubject.asObservable();

  constructor(private http: HttpClient, private router: Router, private authApi: AuthApiService) {
    const stored = localStorage.getItem(this.USER_KEY);
    if (stored) {
      this.currentUserSubject.next(JSON.parse(stored));
    }
  }

  login(request: LoginRequest): Observable<{ success: boolean; message: string; data: AuthResponse }> {
    return this.http.post<{ success: boolean; message: string; data: AuthResponse }>(`${environment.apiUrl}/auth/login`, request).pipe(
      tap(response => {
        const authData = response.data;
        if (authData.twoFactorRequired && authData.tempToken) {
          this.twoFactorRequiredSubject.next(true);
          this.tempTokenSubject.next(authData.tempToken);
        } else {
          localStorage.setItem(this.TOKEN_KEY, authData.token);
          localStorage.setItem(this.USER_KEY, JSON.stringify(authData));
          this.currentUserSubject.next(authData);
        }
      })
    );
  }

  login2FA(tempToken: string, code: string): Observable<{ success: boolean; message: string; data: AuthResponse }> {
    return this.authApi.verifyLogin2FA(tempToken, code).pipe(
      tap((response: any) => {
        const authData = response.data;
        localStorage.setItem(this.TOKEN_KEY, authData.token);
        localStorage.setItem(this.USER_KEY, JSON.stringify(authData));
        this.currentUserSubject.next(authData);
        this.twoFactorRequiredSubject.next(false);
        this.tempTokenSubject.next(null);
      })
    ) as any;
  }

  clear2FAState(): void {
    this.twoFactorRequiredSubject.next(false);
    this.tempTokenSubject.next(null);
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.twoFactorRequiredSubject.next(false);
    this.tempTokenSubject.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  getUserRole(): string | null {
    return this.currentUserSubject.value?.role ?? null;
  }
}
