import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { PayrollItemResponse } from '../../core/models/payroll.model';
import { CompanySettings } from '../../core/models/settings.model';

interface PaySlipLine {
  label: string;
  base: string;
  taux: string;
  montant: number;
}

@Component({
  selector: 'app-pay-slip-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatIconModule, MatButtonModule],
  templateUrl: './pay-slip-dialog.component.html',
  styleUrl: './pay-slip-dialog.component.scss'
})
export class PaySlipDialogComponent {
  record: PayrollItemResponse;
  settings: CompanySettings;
  refNumber: string;
  paymentDate: string;
  workedDays: number;
  totalWorkDays: number;
  periodLabel: string;
  companyName: string;
  companyNameShort: string;
  companyAddress: string;
  companySector: string;
  companyEmail: string;
  companyPhone: string;
  companyLogo: string | null;
  employeePhoto: string | null;

  remunerations: PaySlipLine[] = [];
  cotisations: PaySlipLine[] = [];
  brutTotal = 0;
  totalRetenues = 0;

  private monthNames = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];

  constructor(
    private dialogRef: MatDialogRef<PaySlipDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { item: PayrollItemResponse; settings: CompanySettings; month: number; year: number }
  ) {
    this.record = data.item;
    this.settings = data.settings;
    this.periodLabel = `${this.monthNames[data.month - 1]} ${data.year}`;
    this.refNumber = `FP-${data.year}-${String(data.month).padStart(2, '0')}-${String(this.record.id).padStart(3, '0')}`;
    const now = new Date();
    this.paymentDate = `${now.getDate()} ${this.monthNames[now.getMonth()]} ${now.getFullYear()}`;
    this.workedDays = this.record.daysWorked || 0;
    this.totalWorkDays = this.countScheduledDaysInMonth(data.month, data.year);
    if (this.totalWorkDays <= 0) this.totalWorkDays = this.record.scheduledWorkDays || Math.max(26, this.workedDays);

    this.companyName = this.settings?.companyName || 'Sepab Agro';
    this.companyNameShort = this.companyName.split(' ')[0];
    this.companyAddress = this.settings?.companyAddress || 'Rue Farhat Hached, Morneg, Ben Arous';
    this.companySector = this.settings?.companySector || '';
    this.companyEmail = this.settings?.companyEmail || '';
    this.companyPhone = this.settings?.companyPhone || '';
    this.companyLogo = this.settings?.companyLogo || null;
    this.employeePhoto = this.record.photo || null;

    this.buildPaySlip();
  }

  private countScheduledDaysInMonth(month: number, year: number): number {
    if (this.record.scheduledWorkDays && this.record.scheduledWorkDays > 0) {
      return this.record.scheduledWorkDays;
    }
    let schedule: Record<string, { start: string; end: string }> = {};
    if (this.record.weeklySchedule) {
      try { schedule = JSON.parse(this.record.weeklySchedule); } catch {}
    }
    const daysInMonth = new Date(year, month, 0).getDate();
    const dayMap = ['DIM', 'LUN', 'MAR', 'MER', 'JEU', 'VEN', 'SAM'];
    let count = 0;
    for (let d = 1; d <= daysInMonth; d++) {
      const date = new Date(year, month - 1, d);
      if (schedule[dayMap[date.getDay()]]) count++;
    }
    return count;
  }

  private computeDeductions(gross: number): { cnss: number; assurance: number; ir: number; css: number } {
    const s = this.settings;
    const cnssRate = s.cnssRate || 9.18;
    const assuranceRate = s.assuranceRate || 0.5;
    const cnssCeiling = s.cnssCeiling || 5173.085;
    const fraisProPercent = s.irFraisProPercent || 10;
    const fraisProCap = s.irFraisProCap || 2000;
    const cssRate = s.irCssRate || 0.5;
    const abatement = s.irAbatement || 0;

    const cnssBase = Math.min(gross, cnssCeiling);
    const cnss = cnssBase * cnssRate / 100;
    const assurance = cnssBase * assuranceRate / 100;
    const social = cnss + assurance;

    const annualGross = gross * 12;
    const annualSocial = social * 12;
    const fraisPro = Math.min(annualGross * fraisProPercent / 100, fraisProCap);
    const annualTaxable = Math.max(0, annualGross - annualSocial - fraisPro - abatement);

    const t1 = s.irTranche1 || 5000;
    const t2 = s.irTranche2 || 10000;
    const t3 = s.irTranche3 || 20000;
    const t4 = s.irTranche4 || 30000;
    const t5 = s.irTranche5 || 40000;
    const t6 = s.irTranche6 || 50000;
    const t7 = s.irTranche7 || 70000;
    const r1 = (s.irRate1 || 0) / 100;
    const r2 = (s.irRate2 || 0) / 100;
    const r3 = (s.irRate3 || 0) / 100;
    const r4 = (s.irRate4 || 0) / 100;
    const r5 = (s.irRate5 || 0) / 100;
    const r6 = (s.irRate6 || 0) / 100;
    const r7 = (s.irRate7 || 0) / 100;
    const r8 = (s.irRate8 || 0) / 100;

    const thresholds = [t1, t2, t3, t4, t5, t6, t7];
    const rates = [r1, r2, r3, r4, r5, r6, r7, r8];
    let remaining = annualTaxable;
    let annualTax = 0;
    let lower = 0;
    for (let i = 0; i < thresholds.length; i++) {
      if (remaining <= 0) break;
      const c = Math.min(remaining, thresholds[i] - lower);
      annualTax += c * rates[i];
      remaining -= c;
      lower = thresholds[i];
    }
    if (remaining > 0) annualTax += remaining * r8;

    const ir = annualTax / 12;
    const css = annualTaxable * cssRate / 100 / 12;

    return {
      cnss: Math.round(cnss * 100) / 100,
      assurance: Math.round(assurance * 100) / 100,
      ir: Math.round(ir * 100) / 100,
      css: Math.round(css * 100) / 100
    };
  }

  private buildPaySlip(): void {
    const r = this.record;
    const s = this.settings;

    const gross = (r.baseSalary || 0) + (r.primeTransport || 0) + (r.primePerformance || 0) + (r.primeOther || 0) + (r.overtimeAmount || 0);

    const cnssRate = s.cnssRate || 9.18;
    const assuranceRate = s.assuranceRate || 0.5;

    const ded = this.computeDeductions(gross);

    this.remunerations = [
      { label: 'Salaire de base', base: this.fmt(r.baseSalary) + ' DT', taux: '—', montant: r.baseSalary },
      { label: 'Prime de transport', base: '—', taux: '—', montant: r.primeTransport || 0 },
      { label: 'Prime de rendement', base: '—', taux: '—', montant: r.primePerformance || 0 },
      { label: 'Autres primes', base: '—', taux: '—', montant: r.primeOther || 0 },
      { label: 'Heures supplémentaires', base: (r.overtimeHours || 0) + ' h', taux: '×' + (s.overtimeRate || 1.5), montant: r.overtimeAmount || 0 },
    ];

    const baseLabel = this.fmt(gross) + ' DT';
    this.cotisations = [
      { label: 'CNSS (salarié)', base: baseLabel, taux: cnssRate + '%', montant: ded.cnss },
      { label: 'Assurance maladie', base: baseLabel, taux: assuranceRate + '%', montant: ded.assurance },
      { label: 'IR (barème progressif)', base: '—', taux: 'barème', montant: ded.ir },
      { label: 'CSS (Contribution Solidarité)', base: '—', taux: (s.irCssRate || 0.5) + '%', montant: ded.css },
    ];

    this.cotisations.push({
      label: 'Absence',
      base: (r.daysAbsent || 0) + ' jour(s) · ' + this.fmt(r.absenceHours || 0) + ' h',
      taux: this.fmt(r.hourlyRate || 0) + ' DT/h',
      montant: r.absenceDeduction
    });

    this.cotisations.push({
      label: 'Heures manquées',
      base: this.fmt(r.missingHours || 0) + ' h',
      taux: this.fmt(r.hourlyRate || 0) + ' DT/h',
      montant: r.missingHoursDeduction
    });

    const minuteRateVal = r.minuteRate || ((r.baseSalary || 0) / 208 / 60);
    this.cotisations.push({
      label: 'Retard',
      base: r.lateMinutes + ' min',
      taux: this.fmt(minuteRateVal) + ' DT/min',
      montant: r.lateDeduction
    });

    this.brutTotal = this.remunerations.reduce((s, l) => s + l.montant, 0);
    this.totalRetenues = this.cotisations.reduce((s, l) => s + l.montant, 0);
  }

  get netSalary(): number {
    return this.record.netSalary || Math.max(0, this.brutTotal - this.totalRetenues);
  }

  getNetLetter(): string {
    const n = this.netSalary;
    return `Arrêté à la somme de ${n.toLocaleString('fr-FR')} Dinars Tunisiens`;
  }

  fmt(n: number): string {
    return (n || 0).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  private employeeAvatarHtml(): string {
    if (this.employeePhoto) {
      return `<img src="${this.employeePhoto}" style="width:44px;height:44px;border-radius:50%;object-fit:cover;flex-shrink:0" alt="${this.record.firstName}">`;
    }
    return `<div style="width:44px;height:44px;border-radius:50%;background:${this.record.avatarColor};display:flex;align-items:center;justify-content:center;color:#fff;font-size:15px;font-weight:700;flex-shrink:0">${this.record.initials}</div>`;
  }

  private companyLogoHtml(): string {
    if (this.companyLogo) {
      return `<img src="${this.companyLogo}" style="height:48px;object-fit:contain;margin-bottom:4px" alt="${this.companyName}">`;
    }
    return '';
  }

  onPrint(): void {
    const r = this.record;
    const companyName = this.settings?.companyName || 'Sepab Agro';
    const companyAddress = this.settings?.companyAddress || 'Rue Farhat Hached, Morneg, Ben Arous';
    const companySector = this.settings?.companySector || '';
    const companyEmail = this.settings?.companyEmail || '';
    const companyPhone = this.settings?.companyPhone || '';
    const companyNameShort = companyName.split(' ')[0];

    const logoHtml = this.companyLogoHtml();
    const avatarHtml = this.employeeAvatarHtml();

    const remunerationsHtml = this.remunerations.map(l =>
      `<tr>
        <td>${l.label}</td>
        <td>${l.base}</td>
        <td>${l.taux}</td>
        <td style="text-align:right;font-weight:700;color:#16A34A">+${this.fmt(l.montant)}</td>
      </tr>`
    ).join('');

    const cotisationsHtml = this.cotisations.map(l =>
      `<tr>
        <td>${l.label}</td>
        <td>${l.base}</td>
        <td>${l.taux}</td>
        <td style="text-align:right;font-weight:700;color:#DC2626">-${this.fmt(l.montant)}</td>
      </tr>`
    ).join('');

    const companyInfoLine = [
      `<strong>${companyName}</strong>`,
      companyAddress,
      companySector,
      companyEmail,
      companyPhone,
    ].filter(Boolean).join(' <span style="color:#CBD5E1;margin:0 6px">·</span> ');

    const html = `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Fiche de Paie - ${r.firstName} ${r.lastName}</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: 'Inter', sans-serif; background: #F1F5F9; color: #0F1420; padding: 16px; }
  .container { max-width: 780px; margin: 0 auto; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06); border: 1px solid #E5E7EB; }
  .content { padding: 24px 32px 16px; }
  .company-section { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
  .company-logo { height: 48px; object-fit: contain; margin-bottom: 4px; }
  .company-name { font-size: 24px; font-weight: 800; color: #0F1420; letter-spacing: -0.02em; }
  .company-details { font-size: 12px; color: #64748B; margin-top: 1px; line-height: 1.4; }
  .ref-info { text-align: right; }
  .ref-title { font-size: 15px; font-weight: 800; color: #2563EB; letter-spacing: 0.04em; display: block; }
  .ref-detail { font-size: 13px; color: #64748B; display: block; margin-top: 2px; }
  .divider { height: 1px; background: #E2E8F0; margin-bottom: 16px; }
  .info-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }
  .info-card { background: #F8FAFC; border: 1px solid #E5E7EB; border-radius: 12px; overflow: hidden; }
  .info-card-header { padding: 8px 16px; background: #F1F5F9; border-bottom: 1px solid #E5E7EB; font-size: 11px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.08em; }
  .info-card-body { padding: 12px 16px; }
  .employee-row { display: flex; align-items: center; gap: 14px; }
  .avatar-sm { width: 44px; height: 44px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 15px; font-weight: 700; flex-shrink: 0; object-fit: cover; }
  .employee-name { display: block; font-size: 14px; font-weight: 700; color: #0F1420; }
  .employee-position { display: block; font-size: 12px; color: #64748B; }
  .employee-contract { display: block; font-size: 11px; color: #94A3B8; margin-top: 1px; }
  .info-row { display: flex; align-items: baseline; gap: 6px; margin-bottom: 3px; }
  .info-row:last-child { margin-bottom: 0; }
  .info-label { font-size: 13px; color: #64748B; }
  .info-value { font-size: 13px; font-weight: 600; color: #0F1420; }
  .table-wrapper { border-radius: 12px; overflow: hidden; border: 1px solid #E5E7EB; margin-bottom: 16px; }
  table { width: 100%; border-collapse: collapse; }
  thead tr { background: linear-gradient(135deg, #0F172A 0%, #1e293b 100%); }
  th { padding: 10px 16px; font-size: 11px; font-weight: 700; color: #fff; text-transform: uppercase; letter-spacing: 0.06em; text-align: left; white-space: nowrap; }
  th:nth-child(2), th:nth-child(3) { text-align: center; }
  th:last-child { text-align: right; }
  td { padding: 10px 16px; font-size: 13px; color: #374151; }
  td:nth-child(2), td:nth-child(3) { text-align: center; color: #64748B; }
  td:last-child { text-align: right; font-weight: 700; font-size: 15px; }
  tbody tr { border-bottom: 1px solid #F1F5F9; }
  tbody tr:last-child { border-bottom: none; }
  .section-row td { background: #F1F5F9; font-size: 11px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.08em; padding: 7px 16px; text-align: left !important; }
  .summary-box { display: flex; justify-content: space-between; align-items: center; background: #111827; border-radius: 14px; padding: 26px 30px; margin-bottom: 28px; }
  .summary-label { font-size: 12px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.08em; display: block; }
  .summary-amount { font-size: 38px; font-weight: 800; color: #fff; line-height: 1.15; display: block; }
  .summary-amount::after { content: ' DT'; font-size: 18px; font-weight: 600; color: #9CA3AF; }
  .summary-letter { font-size: 12px; color: #6B7280; margin-top: 4px; font-style: italic; display: block; }
  .summary-right { display: flex; flex-direction: column; gap: 10px; text-align: right; }
  .summary-detail-label { font-size: 12px; color: #6B7280; display: block; }
  .summary-detail-value { font-size: 16px; font-weight: 700; display: block; }
  .summary-detail-value.blue { color: #60A5FA; }
  .summary-detail-value.red { color: #F87171; }
  .signatures-divider { height: 1px; background: #E2E8F0; margin-bottom: 28px; }
  .signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 60px; }
  .signature-block { display: flex; flex-direction: column; }
  .signature-label { font-size: 13px; color: #94A3B8; font-weight: 500; margin-bottom: 40px; }
  .signature-line { height: 1px; background: #CBD5E1; width: 100%; }
  .signature-name { font-size: 13px; font-weight: 600; color: #64748B; margin-top: 8px; }
  .company-info-bottom { text-align: center; font-size: 12px; color: #64748B; line-height: 1.6; margin-top: 24px; padding-top: 18px; border-top: 1px solid #E2E8F0; }
  .company-info-bottom strong { color: #0F1420; font-weight: 700; font-size: 13px; }
  .footer-note { text-align: center; font-size: 12px; color: #94A3B8; padding-top: 12px; margin-top: 12px; }
  .print-btn-wrapper { display: flex; justify-content: center; padding: 24px; background: #fff; border-top: 1px solid #E5E7EB; }
  .print-btn { display: inline-flex; align-items: center; gap: 10px; padding: 14px 28px; border-radius: 12px; border: none; background: #16A34A; color: #fff; font-size: 15px; font-weight: 700; cursor: pointer; font-family: 'Inter', sans-serif; transition: background 0.2s; }
  .print-btn:hover { background: #15803D; }
  .print-btn svg { width: 20px; height: 20px; }
  @media print {
    body { background: #fff; padding: 0; }
    .container { box-shadow: none; border: none; border-radius: 0; }
    .print-btn-wrapper { display: none !important; }
  }
</style>
</head>
<body>
<div class="container">
  <div class="content">
    <div class="company-section">
      <div style="display:flex;align-items:center;gap:14px">
        ${logoHtml ? `<img src="${this.companyLogo}" style="height:56px;width:56px;object-fit:contain;border-radius:8px;flex-shrink:0">` : ''}
        <div>
          <h3 class="company-name">${companyNameShort}</h3>
          <p class="company-details">${companyName} — ${companyAddress}</p>
        </div>
      </div>
      <div class="ref-info">
        <span class="ref-title">FICHE DE PAIE</span>
        <span class="ref-detail">Période : ${this.periodLabel}</span>
        <span class="ref-detail">Réf. ${this.refNumber}</span>
      </div>
    </div>
    <div class="divider"></div>
    <div class="info-cards">
      <div class="info-card">
        <div class="info-card-header">SALARIÉ</div>
        <div class="info-card-body">
          <div class="employee-row">
            ${avatarHtml}
            <div>
              <span class="employee-name">${r.firstName} ${r.lastName}</span>
              <span class="employee-position">${r.position || ''}</span>
              <span class="employee-contract">${r.contractType || ''} · ${r.department || ''}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="info-card">
        <div class="info-card-header">INFORMATIONS PAIE</div>
        <div class="info-card-body">
          <div class="info-row"><span class="info-label">Période :</span> <span class="info-value">${this.periodLabel}</span></div>
          <div class="info-row"><span class="info-label">Date de paiement :</span> <span class="info-value">${this.paymentDate}</span></div>
          <div class="info-row"><span class="info-label">Mode :</span> <span class="info-value">Virement bancaire</span></div>
          <div class="info-row"><span class="info-label">Jours travaillés :</span> <span class="info-value">${this.workedDays} / ${this.totalWorkDays}</span></div>
          ${r.overtimeHours ? `<div class="info-row"><span class="info-label">Heures supplémentaires :</span> <span class="info-value" style="color:#16A34A">${this.fmt(r.overtimeHours)} h</span></div>` : ''}
        </div>
      </div>
    </div>
    <div class="table-wrapper">
      <table>
        <thead><tr><th>LIBELLÉ</th><th>BASE</th><th>TAUX</th><th>MONTANT (DT)</th></tr></thead>
        <tbody>
          <tr class="section-row"><td colspan="4">RÉMUNÉRATIONS</td></tr>
          ${remunerationsHtml}
          <tr class="section-row"><td colspan="4">COTISATIONS &amp; RETENUES SALARIALES</td></tr>
          ${cotisationsHtml}
        </tbody>
      </table>
    </div>
    <div class="summary-box">
      <div>
        <span class="summary-label">SALAIRE NET À PAYER</span>
        <span class="summary-amount">${this.fmt(this.netSalary)}</span>
        <span class="summary-letter">${this.getNetLetter()}</span>
      </div>
      <div class="summary-right">
        <div><span class="summary-detail-label">Salaire brut</span><span class="summary-detail-value blue">${this.fmt(this.brutTotal)} DT</span></div>
        <div><span class="summary-detail-label">Total cotisations</span><span class="summary-detail-value red">${this.fmt(this.totalRetenues)} DT</span></div>
      </div>
    </div>
    <div class="signatures-divider"></div>
    <div class="signatures">
      <div class="signature-block">
        <span class="signature-label">Signature de l'employeur</span>
        <div class="signature-line"></div>
        <span class="signature-name">${companyNameShort}</span>
      </div>
      <div class="signature-block">
        <span class="signature-label">Signature du salarié (lu et approuvé)</span>
        <div class="signature-line"></div>
        <span class="signature-name">${r.firstName} ${r.lastName}</span>
      </div>
    </div>
    <div class="company-info-bottom">
      ${companyInfoLine}
    </div>
    </div>
  </div>
  <div class="print-btn-wrapper">
    <button class="print-btn" onclick="window.print()">
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M6.72 13.829c-.24.03-.48.062-.72.096m.72-.096a42.415 42.415 0 0 1 10.56 0m-10.56 0L6.34 18m10.94-4.171c.24.03.48.062.72.096m-.72-.096L17.66 18m0 0 .229 2.523a1.125 1.125 0 0 1-1.12 1.227H7.231c-.662 0-1.18-.568-1.12-1.227L6.34 18m11.318 0h1.091A2.25 2.25 0 0 0 21 15.75V9.456c0-1.081-.768-2.015-1.837-2.175a48.055 48.055 0 0 0-1.913-.247M6.34 18H5.25A2.25 2.25 0 0 1 3 15.75V9.456c0-1.081.768-2.015 1.837-2.175a48.041 48.041 0 0 1 1.913-.247m0 0a48.159 48.159 0 0 1 8.5 0m-8.5 0V6.75a2 2 0 0 1 2-2h4.5a2 2 0 0 1 2 2v1.015" /></svg>
      Imprimer / Sauvegarder en PDF
    </button>
  </div>
</div>
</body>
</html>`;

    const printWindow = window.open('', '_blank', 'width=900,height=700');
    if (printWindow) {
      printWindow.document.write(html);
      printWindow.document.close();
    }
  }

  onClose(): void {
    this.dialogRef.close();
  }
}
