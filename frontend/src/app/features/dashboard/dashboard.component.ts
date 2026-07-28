import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { Chart, registerables } from 'chart.js';
import { DashboardService, DashboardStats, DashboardChart, RecentAttendance } from '../../core/services/dashboard.service';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatChipsModule, BaseChartDirective],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  greeting = 'Bonjour, Mohamed';
  subtitle = 'Gérez facilement les présences et les salaires de votre entreprise.';

  stats = [
    { label: 'Total Employés', value: '—', trend: '', trendUp: true, icon: 'people', color: '#2563eb', bgColor: '#eff6ff', sparkline: [0,0,0,0,0,0,0,0,0,0,0,0] },
    { label: 'Présents Aujourd\'hui', value: '—', trend: '', trendUp: true, icon: 'person', color: '#22c55e', bgColor: '#f0fdf4', sparkline: [0,0,0,0,0,0,0,0,0,0,0,0] },
    { label: 'Absents', value: '—', trend: '', trendUp: false, icon: 'person_off', color: '#ef4444', bgColor: '#fef2f2', sparkline: [0,0,0,0,0,0,0,0,0,0,0,0] },
    { label: 'Retards', value: '—', trend: '', trendUp: false, icon: 'schedule', color: '#f59e0b', bgColor: '#fffbeb', sparkline: [0,0,0,0,0,0,0,0,0,0,0,0] }
  ];

  days = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  weeklyChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      { label: 'Présents', data: [], backgroundColor: '#2563EB', borderRadius: 5, barPercentage: 0.6, categoryPercentage: 0.7 },
      { label: 'Retards', data: [], backgroundColor: '#F59E0B', borderRadius: 5, barPercentage: 0.6, categoryPercentage: 0.7 },
      { label: 'Absents', data: [], backgroundColor: '#22C55E', borderRadius: 5, barPercentage: 0.6, categoryPercentage: 0.7 }
    ]
  };

  weeklyChartOptions: ChartOptions<'bar'> = {
    responsive: true, maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { color: '#64748B', font: { size: 12 }, padding: 16, usePointStyle: true, pointStyle: 'circle' } },
      tooltip: { backgroundColor: '#fff', titleColor: '#0F172A', bodyColor: '#374151', borderColor: '#E2E8F0', borderWidth: 1, cornerRadius: 10, padding: 12, displayColors: true }
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 11 } }, border: { display: false } },
      y: { min: 0, max: 8, grid: { color: '#F1F5F9', drawTicks: false }, ticks: { color: '#94a3b8', font: { size: 11 }, padding: 8, stepSize: 1 }, border: { display: false } }
    }
  };

  weekLabel = '';
  todayLabel = '';

  summaryItems = [
    { label: 'Taux de présence', value: '—', color: '#2563eb', dot: '#2563eb' },
    { label: 'Arrivées à l\'heure', value: '—', color: '#22c55e', dot: '#22c55e' },
    { label: 'Retards', value: '—', color: '#f59e0b', dot: '#f59e0b' },
    { label: 'Absences justifiées', value: '—', color: '#8b5cf6', dot: '#8b5cf6' },
    { label: 'Absences non just.', value: '—', color: '#ef4444', dot: '#ef4444' }
  ];

  productivity = 0;
  recentAttendances: any[] = [];
  loading = true;
  loadingStats = true;
  loadingChart = true;
  loadingRecent = true;

  constructor(private dashboardService: DashboardService) {
    const now = new Date();
    const day = now.getDate();
    const monthNames = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
    this.todayLabel = `${day} ${monthNames[now.getMonth()]} ${now.getFullYear()}`;
  }

  ngOnInit(): void {
    this.loadStats();
    this.loadChart();
    this.loadRecent();
  }

  loadStats(): void {
    this.loadingStats = true;
    this.dashboardService.getStats().subscribe({
      next: (res) => {
        const s = res.data;
        const total = s.totalEmployees || 1;
        this.stats[0].value = String(s.totalEmployees);
        this.stats[1].value = String(s.presentToday);
        this.stats[2].value = String(s.absentToday);
        this.stats[3].value = String(s.lateToday);

        this.summaryItems[0].value = total > 0 ? `${Math.round(s.presentToday / total * 100)}%` : '0%';
        this.summaryItems[1].value = `${s.presentToday - s.lateToday} emp.`;
        this.summaryItems[2].value = `${s.lateToday} emp.`;
        this.summaryItems[3].value = `—`;
        this.summaryItems[4].value = `${s.absentToday} emp.`;

        this.productivity = total > 0 ? Math.round((s.presentToday / total) * 100 * 10) / 10 : 0;
        this.loadingStats = false;
        this.checkLoading();
      },
      error: () => { this.loadingStats = false; this.checkLoading(); }
    });
  }

  loadChart(): void {
    this.loadingChart = true;
    this.dashboardService.getChart().subscribe({
      next: (res) => {
        const c = res.data;
        const totalEmp = c.totalEmployees || 8;
        const yMax = Math.max(totalEmp, 2);

        this.weekLabel = c.weekLabel || '';

        this.weeklyChartData = {
          labels: c.labels,
          datasets: [
            { label: 'Présents', data: c.present, backgroundColor: '#2563EB', borderRadius: 5, barPercentage: 0.6, categoryPercentage: 0.7 },
            { label: 'Retards', data: c.late || [], backgroundColor: '#F59E0B', borderRadius: 5, barPercentage: 0.6, categoryPercentage: 0.7 },
            { label: 'Absents', data: c.absent, backgroundColor: '#22C55E', borderRadius: 5, barPercentage: 0.6, categoryPercentage: 0.7 }
          ]
        };

        this.weeklyChartOptions = {
          ...this.weeklyChartOptions,
          scales: {
            x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 11 } }, border: { display: false } },
            y: { min: 0, max: yMax, grid: { color: '#F1F5F9', drawTicks: false }, ticks: { color: '#94a3b8', font: { size: 11 }, padding: 8, stepSize: 1 }, border: { display: false } }
          }
        };
        this.loadingChart = false;
        this.checkLoading();
      },
      error: () => { this.loadingChart = false; this.checkLoading(); }
    });
  }

  loadRecent(): void {
    this.loadingRecent = true;
    this.dashboardService.getRecent().subscribe({
      next: (res) => {
        this.recentAttendances = res.data.map((r: RecentAttendance) => ({
          name: `${r.firstName} ${r.lastName}`,
          position: r.position,
          checkIn: r.checkIn || '—',
          checkOut: r.checkOut || '—',
          hours: r.workedHours > 0 ? `${Math.floor(r.workedHours)}h${String(Math.round((r.workedHours % 1) * 60)).padStart(2, '0')}` : '0h00',
          status: this.mapStatus(r.status),
          initials: r.initials,
          color: r.avatarColor
        }));
        this.loadingRecent = false;
        this.checkLoading();
      },
      error: () => { this.loadingRecent = false; this.checkLoading(); }
    });
  }

  private checkLoading(): void {
    if (!this.loadingStats && !this.loadingChart && !this.loadingRecent) {
      this.loading = false;
    }
  }

  mapStatus(status: string): string {
    switch (status) {
      case 'PRESENT': return 'Présent';
      case 'PARTIAL': return 'Retard';
      case 'ABSENT': return 'Absent';
      default: return status;
    }
  }

  getSparklinePath(points: number[]): string {
    const max = Math.max(...points);
    const min = Math.min(...points);
    const range = max - min || 1;
    const width = 80;
    const height = 30;
    const step = width / (points.length - 1);
    return points.map((p, i) => {
      const x = i * step;
      const y = height - ((p - min) / range) * height;
      return `${i === 0 ? 'M' : 'L'}${x},${y}`;
    }).join(' ');
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'Présent': return 'status-present';
      case 'Retard': return 'status-late';
      case 'Absent': return 'status-absent';
      default: return '';
    }
  }
}
