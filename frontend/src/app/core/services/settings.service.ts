import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';
import { CompanySettings } from '../models/settings.model';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly apiUrl = `${environment.apiUrl}/settings`;

  constructor(private http: HttpClient) {}

  get(): Observable<ApiResponse<CompanySettings>> {
    return this.http.get<ApiResponse<CompanySettings>>(this.apiUrl);
  }

  update(settings: CompanySettings): Observable<ApiResponse<CompanySettings>> {
    return this.http.put<ApiResponse<CompanySettings>>(this.apiUrl, settings);
  }
}
