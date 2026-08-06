import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { Chart, registerables } from 'chart.js';
import { openPdfWindow, PdfCompanySettings } from '../../shared/pdf-export.util';
import { ReportsService, ReportData, EmployeeAttendanceStats } from '../../core/services/reports.service';
import { SettingsService } from '../../core/services/settings.service';
import { CompanySettings } from '../../core/models/settings.model';

Chart.register(...registerables);

@Component({
  selector: 'app-report-view',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, BaseChartDirective],
  templateUrl: './report-view.component.html',
  styleUrl: './report-view.component.scss'
})
export class ReportViewComponent implements OnInit {
  selectedPeriod: 'Mensuel' | 'Annuel' = 'Mensuel';
  months = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
  selectedMonth = this.months[new Date().getMonth()];
  years = [String(new Date().getFullYear() - 2), String(new Date().getFullYear() - 1), String(new Date().getFullYear())];
  selectedYear = String(new Date().getFullYear());

  private reportData: ReportData | null = null;
  settings: CompanySettings | null = null;
  loading = false;

  constructor(private reportsService: ReportsService, private settingsService: SettingsService) {}

  ngOnInit(): void {
    this.settingsService.get().subscribe({
      next: (res) => this.settings = res.data,
      error: () => {}
    });
    this.loadReport();
  }

  loadReport(): void {
    this.loading = true;
    if (this.selectedPeriod === 'Mensuel') {
      const monthIndex = this.months.indexOf(this.selectedMonth) + 1;
      const year = parseInt(this.selectedYear);
      this.reportsService.getMonthly(monthIndex, year).subscribe({
        next: (res) => { this.reportData = res.data; this.loading = false; },
        error: () => { this.reportData = this.getEmptyData(); this.loading = false; }
      });
    } else {
      const year = parseInt(this.selectedYear);
      this.reportsService.getAnnual(year).subscribe({
        next: (res) => { this.reportData = res.data; this.loading = false; },
        error: () => { this.reportData = this.getEmptyData(); this.loading = false; }
      });
    }
  }

  onPeriodChange(): void { this.loadReport(); }
  onMonthChange(): void { this.loadReport(); }
  onYearChange(): void { this.loadReport(); }

  private getEmptyData(): ReportData {
    return { labels: [], presence: [], retards: [], masse: [], overtimeHours: [], totalEmployees: 0, absences: [], employeeStats: [] };
  }

  get d(): ReportData {
    return this.reportData || this.getEmptyData();
  }

  get kpis() {
    const avgPresence = this.d.presence.length > 0 ? Math.round(this.d.presence.reduce((a, b) => a + b, 0) / this.d.presence.length * 10) / 10 : 0;
    const totalRetards = this.d.retards.reduce((a, b) => a + b, 0);
    const totalAbsences = this.d.absences.reduce((a, b) => a + b.value, 0);
    const totalOvertime = this.d.overtimeHours.reduce((a, b) => a + b, 0);
    const totalDaysPresent = this.d.employeeStats.reduce((a, b) => a + b.daysPresent, 0);
    const periodSuffix = this.selectedPeriod === 'Mensuel' ? 'du mois' : "de l'année";

    return [
      { label: `Taux de Présence`, value: `${avgPresence}%`, sub: `Moyenne ${periodSuffix}`, color: '#2563EB', icon: 'trending_up' },
      { label: `Retards ${periodSuffix}`, value: `${totalRetards}`, sub: totalRetards < 20 ? 'Bon' : totalRetards < 80 ? 'Moyen' : 'Élevé', color: '#F59E0B', icon: 'schedule' },
      { label: `Absences ${periodSuffix}`, value: `${totalAbsences}`, sub: `${totalAbsences} jours`, color: '#EF4444', icon: 'event_busy' },
      { label: `Heures Supplémentaires`, value: `${Math.round(totalOvertime)}h`, sub: `${totalOvertime > 0 ? 'Enregistrées' : 'Aucune'}`, color: '#8B5CF6', icon: 'more_time' },
    ];
  }

  // ─── Charts ───

  get presenceChartData(): ChartData<'line'> {
    return {
      labels: this.d.labels,
      datasets: [{
        data: this.d.presence,
        borderColor: '#2563EB', backgroundColor: 'rgba(37, 99, 235, 0.08)',
        fill: true, tension: 0.4,
        pointBackgroundColor: '#2563EB', pointBorderColor: '#fff', pointBorderWidth: 2,
        pointRadius: 5, pointHoverRadius: 7, borderWidth: 2.5,
      }]
    };
  }

  get lateChartData(): ChartData<'bar'> {
    return {
      labels: this.d.labels,
      datasets: [{
        data: this.d.retards,
        backgroundColor: '#F59E0B', borderRadius: 5,
        barThickness: this.selectedPeriod === 'Mensuel' ? 40 : 22,
      }]
    };
  }

  get overtimeChartData(): ChartData<'line'> {
    return {
      labels: this.d.labels,
      datasets: [{
        data: this.d.overtimeHours,
        borderColor: '#8B5CF6', backgroundColor: 'rgba(139, 92, 246, 0.08)',
        fill: true, tension: 0.4,
        pointBackgroundColor: '#8B5CF6', pointBorderColor: '#fff', pointBorderWidth: 2,
        pointRadius: 5, pointHoverRadius: 7, borderWidth: 2.5,
      }]
    };
  }

  get masseChartData(): ChartData<'line'> {
    return {
      labels: this.d.labels,
      datasets: [{
        data: this.d.masse,
        borderColor: '#10B981', backgroundColor: 'rgba(16, 185, 129, 0.08)',
        fill: true, tension: 0.4,
        pointBackgroundColor: '#10B981', pointBorderColor: '#fff', pointBorderWidth: 2,
        pointRadius: 5, pointHoverRadius: 7, borderWidth: 2.5,
      }]
    };
  }

  get absenceChartData(): ChartData<'doughnut'> {
    return {
      labels: this.d.absences.map(a => a.name),
      datasets: [{
        data: this.d.absences.map(a => a.value),
        backgroundColor: this.d.absences.map(a => a.color),
        borderWidth: 0, hoverOffset: 4,
      }]
    };
  }

  // ─── Chart Options ───

  get presenceChartOptions(): ChartOptions<'line'> {
    return {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { backgroundColor: '#fff', titleColor: '#0F172A', bodyColor: '#374151', borderColor: '#E2E8F0', borderWidth: 1, cornerRadius: 10, padding: 12, displayColors: false, callbacks: { label: (ctx) => `${ctx.parsed.y}%` } }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 11 } }, border: { display: false } },
        y: { min: 0, max: 100, grid: { color: '#F1F5F9', drawTicks: false }, ticks: { color: '#94a3b8', font: { size: 11 }, padding: 8, callback: (val: number | string) => `${val}%` }, border: { display: false } }
      }
    };
  }

  get lateChartOptions(): ChartOptions<'bar'> {
    return {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { backgroundColor: '#fff', titleColor: '#0F172A', bodyColor: '#374151', borderColor: '#E2E8F0', borderWidth: 1, cornerRadius: 10, padding: 12, displayColors: false, callbacks: { label: (ctx) => `${ctx.parsed.y} retards` } }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 11 } }, border: { display: false } },
        y: { min: 0, grid: { color: '#F1F5F9', drawTicks: false }, ticks: { color: '#94a3b8', font: { size: 11 }, padding: 8 }, border: { display: false } }
      }
    };
  }

  get overtimeChartOptions(): ChartOptions<'line'> {
    return {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { backgroundColor: '#fff', titleColor: '#0F172A', bodyColor: '#374151', borderColor: '#E2E8F0', borderWidth: 1, cornerRadius: 10, padding: 12, displayColors: false, callbacks: { label: (ctx) => `${ctx.parsed.y}h` } }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 11 } }, border: { display: false } },
        y: { min: 0, grid: { color: '#F1F5F9', drawTicks: false }, ticks: { color: '#94a3b8', font: { size: 11 }, padding: 8 }, border: { display: false } }
      }
    };
  }

  get masseChartOptions(): ChartOptions<'line'> {
    return {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { backgroundColor: '#fff', titleColor: '#0F172A', bodyColor: '#374151', borderColor: '#E2E8F0', borderWidth: 1, cornerRadius: 10, padding: 12, displayColors: false, callbacks: { label: (ctx) => `${(ctx.parsed.y ?? 0).toLocaleString('fr-FR')} DT` } }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 11 } }, border: { display: false } },
        y: { grid: { color: '#F1F5F9', drawTicks: false }, ticks: { color: '#94a3b8', font: { size: 11 }, padding: 8, callback: (val: number | string) => `${(Number(val) / 1000).toFixed(0)}k` }, border: { display: false } }
      }
    };
  }

  get absenceChartOptions(): ChartOptions<'doughnut'> {
    return {
      responsive: true, maintainAspectRatio: false, cutout: '60%',
      plugins: {
        legend: { position: 'right', labels: { color: '#64748B', font: { size: 12 }, padding: 14, usePointStyle: true, pointStyle: 'circle' } },
        tooltip: { backgroundColor: '#fff', titleColor: '#0F172A', bodyColor: '#374151', borderColor: '#E2E8F0', borderWidth: 1, cornerRadius: 10, padding: 12, callbacks: { label: (ctx) => ` ${ctx.label}: ${ctx.parsed} jours` } }
      }
    };
  }

  // ─── Helpers ───

  get topRetards(): EmployeeAttendanceStats[] {
    return this.d.employeeStats
      .filter(e => e.daysLate > 0)
      .sort((a, b) => b.daysLate - a.daysLate)
      .slice(0, 5);
  }

  get topAbsent(): EmployeeAttendanceStats[] {
    return this.d.employeeStats
      .filter(e => e.daysAbsent > 0)
      .sort((a, b) => b.daysAbsent - a.daysAbsent)
      .slice(0, 5);
  }

  get topOvertime(): EmployeeAttendanceStats[] {
    return this.d.employeeStats
      .filter(e => e.overtimeHours > 0)
      .sort((a, b) => b.overtimeHours - a.overtimeHours)
      .slice(0, 5);
  }

  // ─── Exports ───

  exportCSV(): void {
    const headers = ['Employé', 'Département', 'Jours Présent', 'Jours Retard', 'Jours Absent', 'Heures Sup.'];
    const rows = this.d.employeeStats.map(e =>
      [`${e.firstName} ${e.lastName}`, e.department || '', e.daysPresent, e.daysLate, e.daysAbsent, e.overtimeHours].join(',')
    );
    const csv = [headers.join(','), ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `rapport_${this.selectedPeriod.toLowerCase()}_${this.selectedPeriod === 'Mensuel' ? this.selectedMonth : this.selectedYear}.csv`;
    a.click();
  }

  downloadPDF(): void {
    const periodLabel = this.selectedPeriod === 'Mensuel' ? this.selectedMonth : this.selectedYear;
    const tableHeader = this.selectedPeriod === 'Mensuel' ? '<th>Semaine</th>' : '<th>Mois</th>';
    const periodRows = this.d.labels.map((l, i) =>
      `<tr><td>${l}</td><td><strong>${this.d.presence[i] ?? 0}%</strong></td><td>${this.d.retards[i] ?? 0}</td><td>${Math.round(this.d.overtimeHours[i] ?? 0)}h</td></tr>`
    ).join('');
    const absRows = (this.d.absences || []).map(a =>
      `<tr><td>${a.name}</td><td><strong>${a.value}</strong> jours</td></tr>`
    ).join('');
    const empRows = this.d.employeeStats.map(e =>
      `<tr><td>${e.firstName} ${e.lastName}</td><td>${e.department || '—'}</td><td>${e.daysPresent}</td><td>${e.daysLate}</td><td>${e.daysAbsent}</td><td>${e.overtimeHours}h</td></tr>`
    ).join('');
    const kpiHtml = this.kpis.map(k =>
      `<div class="kpi-item"><span class="kpi-value" style="color:${k.color}">${k.value}</span><span class="kpi-label">${k.label}</span></div>`
    ).join('');

    const contentHtml = `
      <div class="section-title">Indicateurs Clés</div>
      <div class="kpi-grid">${kpiHtml}</div>
      <div class="section-title">Présence & Ponctualité</div>
      <table><thead><tr>${tableHeader}<th>Taux Présence</th><th>Retards</th><th>Heures Sup.</th></tr></thead><tbody>${periodRows}</tbody></table>
      <div class="section-title">Répartition des Absences</div>
      <table><thead><tr><th>Type d'Absence</th><th>Jours</th></tr></thead><tbody>${absRows}</tbody></table>
      <div class="section-title">Détail par Employé</div>
      <table><thead><tr><th>Employé</th><th>Département</th><th>Présent</th><th>Retard</th><th>Absent</th><th>Heures Sup.</th></tr></thead><tbody>${empRows}</tbody></table>
    `;

    openPdfWindow('Rapport Analytique RH', periodLabel, contentHtml, this.settings as PdfCompanySettings);
  }
}
