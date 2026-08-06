export interface BadgeEmployee {
  firstName: string;
  lastName: string;
  photo?: string | null;
  position?: string | null;
  department?: string | null;
  matricule?: string | null;
  rfidUid?: string | null;
  cin?: string | null;
  contractType?: string | null;
  hiringDate?: string | null;
}

export interface BadgeCompanySettings {
  companyName?: string;
  companyAddress?: string;
  companyEmail?: string;
  companyPhone?: string;
  companyLogo?: string | null;
}

export function openBadgePdfWindow(
  employee: BadgeEmployee,
  settings?: BadgeCompanySettings
): void {
  const companyName = settings?.companyName || 'Sepab Agro';
  const companyAddress = settings?.companyAddress || '';
  const companyEmail = settings?.companyEmail || '';
  const companyPhone = settings?.companyPhone || '';
  const companyLogo = settings?.companyLogo || null;

  const fullName = `${employee.firstName} ${employee.lastName}`;
  const position = employee.position || '';
  const department = employee.department || '';
  const matricule = employee.matricule || '';
  const rfidUid = employee.rfidUid || '';
  const cin = employee.cin || '';
  const contractType = employee.contractType || '';
  const hiringDate = employee.hiringDate
    ? new Date(employee.hiringDate).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })
    : '';

  const photoHtml = employee.photo
    ? `<img src="${employee.photo}" alt="Photo" class="photo">`
    : `<div class="photo-placeholder"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z"/></svg></div>`;

  const logoHtml = companyLogo
    ? `<img src="${companyLogo}" alt="${companyName}" class="logo">`
    : `<div class="logo-placeholder">${companyName.charAt(0)}</div>`;

  const html = `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Badge — ${fullName}</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: 'Inter', -apple-system, sans-serif;
    background: #E2E8F0;
    padding: 40px 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 32px;
  }

  .card {
    width: 85.6mm;
    height: 54mm;
    border-radius: 3mm;
    overflow: hidden;
    position: relative;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    background: #ffffff;
    border: 1px solid #d0d5dd;
    display: flex;
    flex-direction: column;
  }

  /* ── Header ── */
  .header {
    background: linear-gradient(135deg, #0F172A 0%, #1e293b 100%);
    color: #fff;
    padding: 2mm 3mm;
    display: flex;
    align-items: center;
    gap: 2.5mm;
    height: 8.5mm;
    flex-shrink: 0;
  }

  .header .logo {
    height: 6mm;
    width: 6mm;
    object-fit: contain;
    border-radius: 1mm;
    flex-shrink: 0;
  }

  .header .logo-placeholder {
    height: 6mm;
    width: 6mm;
    border-radius: 1mm;
    background: #334155;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 800;
    font-size: 2.8mm;
    flex-shrink: 0;
  }

  .header .company-name {
    font-weight: 700;
    font-size: 3mm;
    letter-spacing: -0.01em;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .header .company-tag {
    margin-left: auto;
    font-size: 1.8mm;
    color: rgba(255,255,255,0.5);
    font-weight: 500;
    letter-spacing: 0.03em;
    text-transform: uppercase;
    border: 0.5px solid rgba(255,255,255,0.15);
    border-radius: 1mm;
    padding: 0.3mm 1mm;
  }

  /* ── Body ── */
  .body {
    flex: 1;
    padding: 2mm 3mm;
    display: flex;
    gap: 2.5mm;
    min-height: 0;
  }

  .body .photo {
    width: 16mm;
    height: 20mm;
    border-radius: 1.5mm;
    object-fit: cover;
    border: 1px solid #e2e8f0;
    flex-shrink: 0;
    align-self: center;
  }

  .body .photo-placeholder {
    width: 16mm;
    height: 20mm;
    border-radius: 1.5mm;
    background: #f1f5f9;
    border: 1px dashed #cbd5e1;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    align-self: center;
  }

  .body .photo-placeholder svg {
    width: 9mm;
    height: 9mm;
    color: #94a3b8;
  }

  .body .info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  .body .info .name {
    font-weight: 800;
    font-size: 4mm;
    color: #0f172a;
    line-height: 1.15;
    margin-bottom: 0.5mm;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .body .info .position {
    font-size: 2.6mm;
    color: #475569;
    font-weight: 600;
    margin-bottom: 0.3mm;
  }

  .body .info .department {
    font-size: 2.4mm;
    color: #64748b;
    margin-bottom: 0.5mm;
  }

  .body .info .matricule-row {
    display: flex;
    align-items: center;
    gap: 1.5mm;
    margin-top: 0.5mm;
    padding-top: 0.5mm;
    border-top: 0.5px solid #e2e8f0;
  }

  .body .info .matricule-row .mat-label {
    font-size: 1.8mm;
    color: #94a3b8;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    font-weight: 600;
  }

  .body .info .matricule-row .mat-value {
    font-size: 2.6mm;
    font-weight: 700;
    color: #0f172a;
    font-family: 'Courier New', monospace;
  }

  /* ── Details row ── */
  .details {
    background: #f8fafc;
    border-top: 1px solid #e2e8f0;
    border-bottom: 1px solid #e2e8f0;
    padding: 1.2mm 3mm;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.5mm 2mm;
    flex-shrink: 0;
  }

  .details .detail-item {
    display: flex;
    align-items: center;
    gap: 1mm;
  }

  .details .detail-item .d-label {
    font-size: 1.8mm;
    color: #94a3b8;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    font-weight: 600;
    white-space: nowrap;
  }

  .details .detail-item .d-value {
    font-size: 2.3mm;
    font-weight: 600;
    color: #0f172a;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .details .detail-item .d-value.mono {
    font-family: 'Courier New', monospace;
    font-size: 2.1mm;
    letter-spacing: 0.02em;
  }

  /* ── Footer ── */
  .footer {
    background: #f1f5f9;
    padding: 1mm 3mm;
    text-align: center;
    font-size: 1.9mm;
    color: #94a3b8;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    flex-shrink: 0;
    height: 5mm;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  /* ── Controls ── */
  .print-btn-wrapper {
    display: flex;
    justify-content: center;
    gap: 12px;
    padding: 20px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    width: 85.6mm;
  }

  .print-btn {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 12px 24px;
    border-radius: 10px;
    border: none;
    font-size: 14px;
    font-weight: 700;
    cursor: pointer;
    font-family: 'Inter', sans-serif;
    transition: background 0.2s;
  }

  .print-btn-primary {
    background: #16A34A;
    color: #fff;
  }

  .print-btn-primary:hover {
    background: #15803D;
  }

  /* ── Print bar ── */
  .print-bar {
    text-align: center;
    margin-top: 8px;
    font-size: 2.2mm;
    color: #64748b;
    width: 85.6mm;
  }

  .print-bar strong {
    color: #dc2626;
    font-weight: 700;
  }

  @media print {
    body {
      background: #fff;
      padding: 0;
      gap: 0;
    }
    .card {
      box-shadow: none;
      page-break-after: always;
    }
    .print-btn-wrapper,
    .print-bar {
      display: none !important;
    }
  }

  @page {
    size: 85.6mm 54mm;
    margin: 0;
  }
</style>
</head>
<body>
  <div class="card">
    <div class="header">
      ${logoHtml}
      <span class="company-name">${companyName}</span>
      <span class="company-tag">Employé</span>
    </div>

    <div class="body">
      ${photoHtml}
      <div class="info">
        <div class="name">${fullName}</div>
        <div class="position">${position}</div>
        <div class="department">${department}</div>
        <div class="matricule-row">
          <span class="mat-label">Matricule</span>
          <span class="mat-value">${matricule || '—'}</span>
        </div>
      </div>
    </div>

    <div class="details">
      <div class="detail-item">
        <span class="d-label">RFID</span>
        <span class="d-value mono">${rfidUid || '—'}</span>
      </div>
      <div class="detail-item">
        <span class="d-label">CIN</span>
        <span class="d-value">${cin || '—'}</span>
      </div>
      <div class="detail-item">
        <span class="d-label">Contrat</span>
        <span class="d-value">${contractType || '—'}</span>
      </div>
      <div class="detail-item">
        <span class="d-label">Embauche</span>
        <span class="d-value">${hiringDate || '—'}</span>
      </div>
    </div>

    <div class="footer">
      ${[companyAddress, companyPhone, companyEmail].filter(Boolean).join(' · ') || 'Sepab Agro'}
    </div>
  </div>

  <div class="print-btn-wrapper">
    <button class="print-btn print-btn-primary" onclick="window.print()">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v8H6z"/></svg>
      Imprimer / Télécharger PDF
    </button>
  </div>
  <div class="print-bar">
    <strong>Important :</strong> Dans la fenêtre d&apos;impression, réglez <strong>« Échelle » → 100 %</strong> (pas de mise à l&apos;échelle) et <strong>marges → aucune</strong> pour un badge aux dimensions réelles (85,6 × 54 mm).
  </div>
</body>
</html>`;

  const w = window.open('', '_blank', 'width=500,height=700');
  if (w) {
    w.document.write(html);
    w.document.close();
  }
}
