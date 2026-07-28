import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';
import { PayrollResponse, PayrollItemResponse, PayrollItemUpdate } from '../models/payroll.model';

@Injectable({ providedIn: 'root' })
export class PayrollService {
  private readonly apiUrl = `${environment.apiUrl}/payrolls`;

  constructor(private http: HttpClient) {}

  generate(month: number, year: number): Observable<ApiResponse<PayrollResponse>> {
    return this.http.post<ApiResponse<PayrollResponse>>(`${this.apiUrl}/generate`, null, {
      params: { month, year }
    });
  }

  getByMonth(month: number, year: number): Observable<ApiResponse<PayrollResponse>> {
    return this.http.get<ApiResponse<PayrollResponse>>(this.apiUrl, {
      params: { month, year }
    });
  }

  getById(id: number): Observable<ApiResponse<PayrollResponse>> {
    return this.http.get<ApiResponse<PayrollResponse>>(`${this.apiUrl}/${id}`);
  }

  updateItem(itemId: number, update: PayrollItemUpdate): Observable<ApiResponse<PayrollItemResponse>> {
    return this.http.put<ApiResponse<PayrollItemResponse>>(`${this.apiUrl}/items/${itemId}`, update);
  }

  payItem(itemId: number): Observable<ApiResponse<PayrollItemResponse>> {
    return this.http.post<ApiResponse<PayrollItemResponse>>(`${this.apiUrl}/items/${itemId}/pay`, {});
  }

  payAll(payrollId: number): Observable<ApiResponse<PayrollResponse>> {
    return this.http.post<ApiResponse<PayrollResponse>>(`${this.apiUrl}/${payrollId}/pay-all`, {});
  }
}
