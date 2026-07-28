import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/employee.model';

export interface TerminalData {
  id: string;
  name: string;
  ipAddress: string;
  rssi: number | null;
  firmwareVersion: string | null;
  freeMemory: number | null;
  uptimeSeconds: number | null;
  scansToday: number;
  status: 'online' | 'offline';
  lastPing: string | null;
}

export interface ScanEvent {
  id: number;
  employeeName: string;
  matricule: string;
  rfidUid: string;
  action: string;
  time: string;
  scannedAt: string;
  deviceId: string;
}

@Injectable({ providedIn: 'root' })
export class Esp32Service {
  private readonly apiUrl = `${environment.apiUrl}/esp32`;

  constructor(private http: HttpClient) {}

  getTerminals(): Observable<TerminalData[]> {
    return this.http.get<any>(`${this.apiUrl}/terminals`).pipe(
      map(res => res.data || [])
    );
  }

  getRecentScans(): Observable<ScanEvent[]> {
    return this.http.get<any>(`${this.apiUrl}/scans/recent`).pipe(
      map(res => res.data || [])
    );
  }
}
