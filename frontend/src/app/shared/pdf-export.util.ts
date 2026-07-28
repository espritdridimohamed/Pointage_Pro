export interface PdfCompanySettings {
  companyName?: string;
  companyAddress?: string;
  companySector?: string;
  companyEmail?: string;
  companyPhone?: string;
  companyLogo?: string | null;
}

export function openPdfWindow(title: string, periodLabel: string, contentHtml: string, settings?: PdfCompanySettings): void {
  const companyName = settings?.companyName || 'Sepab Agro';
  const companyNameShort = companyName.split(' ')[0];
  const companyAddress = settings?.companyAddress || 'Rue Farhat Hached, Morneg, Ben Arous';
  const companySector = settings?.companySector || '';
  const companyEmail = settings?.companyEmail || '';
  const companyPhone = settings?.companyPhone || '';
  const companyLogo = settings?.companyLogo || null;

  const logoHtml = companyLogo
    ? `<img src="${companyLogo}" style="height:48px;width:48px;object-fit:contain;border-radius:8px;flex-shrink:0" alt="${companyName}">`
    : '';

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
<title>${title} — PointagePro</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: 'Inter', sans-serif; background: #F1F5F9; color: #0F172A; padding: 16px; }
  .container { max-width: 860px; margin: 0 auto; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06); border: 1px solid #E5E7EB; }
  .content { padding: 24px 28px 16px; }
  .table-wrapper { overflow-x: auto; }

  .header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
  .header-left { display: flex; align-items: center; gap: 14px; }
  .header-left .company-name { font-size: 24px; font-weight: 800; color: #0F172A; letter-spacing: -0.02em; }
  .header-left .company-details { font-size: 12px; color: #64748B; margin-top: 1px; line-height: 1.4; }
  .header-right { text-align: right; }
  .header-right .doc-type { font-size: 15px; font-weight: 800; color: #2563EB; letter-spacing: 0.04em; display: block; }
  .header-right .doc-period { font-size: 13px; color: #64748B; display: block; margin-top: 2px; }
  .header-right .doc-ref { font-size: 13px; color: #64748B; display: block; margin-top: 2px; }

  .divider { height: 1px; background: #E2E8F0; margin-bottom: 20px; }

  table { width: 100%; border-collapse: collapse; margin-bottom: 16px; table-layout: auto; }
  thead tr { background: linear-gradient(135deg, #0F172A 0%, #1e293b 100%); }
  th { padding: 10px 12px; font-size: 11px; font-weight: 700; color: #fff; text-transform: uppercase; letter-spacing: 0.06em; text-align: left; white-space: nowrap; }
  td { padding: 10px 12px; font-size: 13px; color: #374151; border-bottom: 1px solid #F1F5F9; }
  tbody tr:last-child td { border-bottom: none; }

  .section-title { font-size: 15px; font-weight: 700; color: #0F172A; margin: 24px 0 10px; }

  .kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; }
  .kpi-item { text-align: center; background: #F8FAFC; border: 1px solid #E5E7EB; border-radius: 10px; padding: 14px 10px; }
  .kpi-value { font-size: 20px; font-weight: 800; display: block; }
  .kpi-label { font-size: 11px; color: #64748B; margin-top: 4px; display: block; }

  .company-info-bottom { text-align: center; font-size: 12px; color: #64748B; line-height: 1.6; margin-top: 24px; padding-top: 18px; border-top: 1px solid #E2E8F0; }
  .company-info-bottom strong { color: #0F1420; font-weight: 700; font-size: 13px; }

  .print-btn-wrapper { display: flex; justify-content: center; gap: 12px; padding: 24px; background: #fff; border-top: 1px solid #E5E7EB; }
  .print-btn { display: inline-flex; align-items: center; gap: 10px; padding: 14px 28px; border-radius: 12px; border: none; font-size: 15px; font-weight: 700; cursor: pointer; font-family: 'Inter', sans-serif; transition: background 0.2s; }
  .print-btn-primary { background: #16A34A; color: #fff; }
  .print-btn-primary:hover { background: #15803D; }
  .print-btn-secondary { background: #2563EB; color: #fff; }
  .print-btn-secondary:hover { background: #1d4ed8; }
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
    <div class="header">
      <div class="header-left">
        ${logoHtml}
        <div>
          <h3 class="company-name">${companyNameShort}</h3>
          <p class="company-details">${companyName} — ${companyAddress}</p>
        </div>
      </div>
      <div class="header-right">
        <span class="doc-type">${title}</span>
        <span class="doc-period">Période : ${periodLabel}</span>
        <span class="doc-ref">Généré le ${new Date().toLocaleDateString('fr-FR')}</span>
      </div>
    </div>
    <div class="divider"></div>
    ${contentHtml}
    <div class="company-info-bottom">
      ${companyInfoLine}
    </div>
  </div>
  <div class="print-btn-wrapper">
    <button class="print-btn print-btn-primary" onclick="window.print()">
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M6.72 13.829c-.24.03-.48.062-.72.096m.72-.096a42.415 42.415 0 0 1 10.56 0m-10.56 0L6.34 18m10.94-4.171c.24.03.48.062.72.096m-.72-.096L17.66 18m0 0 .229 2.523a1.125 1.125 0 0 1-1.12 1.227H7.231c-.662 0-1.18-.568-1.12-1.227L6.34 18m11.318 0h1.091A2.25 2.25 0 0 0 21 15.75V9.456c0-1.081-.768-2.015-1.837-2.175a48.055 48.055 0 0 0-1.913-.247M6.34 18H5.25A2.25 2.25 0 0 1 3 15.75V9.456c0-1.081.768-2.015 1.837-2.175a48.041 48.041 0 0 1 1.913-.247m0 0a48.159 48.159 0 0 1 8.5 0m-8.5 0V6.75a2 2 0 0 1 2-2h4.5a2 2 0 0 1 2 2v1.015" /></svg>
      Imprimer / Sauvegarder en PDF
    </button>
  </div>
</div>
</body>
</html>`;

  const w = window.open('', '_blank', 'width=900,height=700');
  if (w) {
    w.document.write(html);
    w.document.close();
  }
}
