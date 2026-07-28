import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private url = environment.apiUrl + '/notifications';

  constructor(private http: HttpClient) {}

  getAll(page = 0, size = 20): Observable<{ notifications: Notification[]; total: number }> {
    return this.http.get<any>(`${this.url}?page=${page}&size=${size}`).pipe(
      map(resp => ({
        notifications: resp.data || [],
        total: resp.totalElements || 0
      }))
    );
  }

  getUnreadCount(): Observable<number> {
    return this.http.get<any>(`${this.url}/unread-count`).pipe(
      map(resp => resp.count || 0)
    );
  }

  markAsRead(id: number): Observable<void> {
    return this.http.put<any>(`${this.url}/${id}/read`, {}).pipe(
      map(() => void 0)
    );
  }

  markAllAsRead(): Observable<void> {
    return this.http.put<any>(`${this.url}/read-all`, {}).pipe(
      map(() => void 0)
    );
  }
}
