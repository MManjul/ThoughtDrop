/**
 * ThoughtDrop Web Studio - Crimson Executive PDF Generator
 * Matches mayankmanjul.com HUD Crimson theme (#ff3366) and ThoughtDrop Android styling
 */

export function exportNotePdf(note) {
  const dateFormatted = new Date(note.createdAt || Date.now()).toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });

  const crimsonPrimary = "#ff3366";
  const crimsonRuby = "#e11d48";
  const crimsonBgTint = "#fff1f3";
  const crimsonBorder = "#fecdd3";

  const keyPoints = Array.isArray(note.keyPoints) ? note.keyPoints : [];
  const actionItems = Array.isArray(note.actionItems) ? note.actionItems : [];

  const keyPointsHtml = keyPoints.length > 0 ? `
    <div class="section">
      <div class="section-title">
        <span class="icon">✦</span> KEY INSIGHTS & CORE DETAILS
      </div>
      <ul class="bullet-list">
        ${keyPoints.map(p => `<li><span class="bullet">•</span><span class="bullet-text">${escapeHtml(p)}</span></li>`).join('')}
      </ul>
    </div>
  ` : '';

  const actionItemsHtml = actionItems.length > 0 ? `
    <div class="section">
      <div class="section-title">
        <span class="icon">✦</span> ACTION ITEMS & NEXT STEPS
      </div>
      <ul class="bullet-list">
        ${actionItems.map(a => `<li><span class="bullet">•</span><span class="bullet-text">${escapeHtml(a)}</span></li>`).join('')}
      </ul>
    </div>
  ` : '';

  const transcriptHtml = note.transcript ? `
    <div class="section">
      <div class="section-title">
        <span class="icon">🎙</span> ORIGINAL RECORDING TRANSCRIPT
      </div>
      <div class="transcript-box">
        ${escapeHtml(note.transcript)}
      </div>
    </div>
  ` : '';

  const printHtml = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <title>${escapeHtml(note.title || 'Untitled Note')} - ThoughtDrop</title>
      <style>
        @page {
          size: A4;
          margin: 18mm 16mm 18mm 16mm;
        }
        * {
          box-sizing: border-box;
          -webkit-print-color-adjust: exact !important;
          print-color-adjust: exact !important;
        }
        body {
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
          background: #ffffff;
          color: #0f172a;
          margin: 0;
          padding: 0;
          font-size: 13.5px;
          line-height: 1.55;
        }
        .header {
          border-bottom: 2px solid ${crimsonPrimary};
          padding-bottom: 12px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-end;
        }
        .brand-title {
          font-size: 15px;
          font-weight: 800;
          letter-spacing: 1.5px;
          color: ${crimsonPrimary};
          text-transform: uppercase;
        }
        .brand-sub {
          font-size: 10.5px;
          color: #64748b;
          font-weight: 500;
          letter-spacing: 0.5px;
          margin-top: 2px;
        }
        .header-meta {
          text-align: right;
        }
        .category-badge {
          display: inline-block;
          font-size: 10.5px;
          font-weight: 700;
          letter-spacing: 0.8px;
          text-transform: uppercase;
          padding: 3px 10px;
          border-radius: 9999px;
          color: ${crimsonRuby};
          background: ${crimsonBgTint};
          border: 1px solid ${crimsonBorder};
        }
        .date-text {
          font-size: 10.5px;
          color: #64748b;
          margin-top: 4px;
        }
        .title {
          font-size: 24px;
          font-weight: 800;
          color: #0f172a;
          line-height: 1.25;
          margin: 0 0 16px 0;
        }
        .summary-card {
          background: #fff5f7;
          border-left: 4px solid ${crimsonPrimary};
          border-radius: 0 8px 8px 0;
          padding: 14px 18px;
          margin-bottom: 22px;
          font-size: 14px;
          line-height: 1.6;
          color: #1e293b;
          border-top: 1px solid #ffe4e6;
          border-right: 1px solid #ffe4e6;
          border-bottom: 1px solid #ffe4e6;
        }
        .section {
          margin-bottom: 20px;
          page-break-inside: avoid;
        }
        .section-title {
          font-size: 11px;
          font-weight: 800;
          letter-spacing: 1.2px;
          color: #475569;
          text-transform: uppercase;
          margin-bottom: 10px;
          display: flex;
          align-items: center;
        }
        .section-title .icon {
          color: ${crimsonPrimary};
          font-size: 13px;
          margin-right: 6px;
        }
        .bullet-list {
          list-style: none;
          padding: 0;
          margin: 0;
        }
        .bullet-list li {
          display: flex;
          align-items: flex-start;
          margin-bottom: 10px;
          font-size: 13.5px;
          line-height: 1.6;
          color: #1e293b;
        }
        .bullet {
          color: ${crimsonPrimary};
          font-weight: 900;
          font-size: 19px;
          line-height: 1.2;
          margin-right: 10px;
          flex-shrink: 0;
        }
        .bullet-text {
          flex: 1;
        }
        .transcript-box {
          background: #f8fafc;
          border: 1px dashed #cbd5e1;
          border-radius: 8px;
          padding: 12px 16px;
          font-size: 12.5px;
          line-height: 1.6;
          color: #64748b;
          font-style: italic;
          white-space: pre-wrap;
        }
        .footer {
          margin-top: 30px;
          padding-top: 12px;
          border-top: 1px solid #fecdd3;
          display: flex;
          justify-content: space-between;
          font-size: 10px;
          color: #94a3b8;
        }
        .footer-brand {
          color: ${crimsonRuby};
          font-weight: 600;
        }
      </style>
    </head>
    <body>
      <div class="header">
        <div>
          <div class="brand-title">ThoughtDrop ✦ Idea Memo</div>
          <div class="brand-sub">Voice-to-AI Personal Intelligence</div>
        </div>
        <div class="header-meta">
          <span class="category-badge">${(note.category || 'Idea').toUpperCase()}</span>
          <div class="date-text">${dateFormatted}</div>
        </div>
      </div>

      <h1 class="title">${escapeHtml(note.title || 'Untitled Thought')}</h1>

      <div class="summary-card">
        ${escapeHtml(note.summary || 'No summary available.')}
      </div>

      ${keyPointsHtml}
      ${actionItemsHtml}
      ${transcriptHtml}

      <div class="footer">
        <span class="footer-brand">ThoughtDrop • Personal & Confidential</span>
        <span>Exported via Web Studio</span>
      </div>

      <script>
        window.onload = function() {
          window.print();
        };
      </script>
    </body>
    </html>
  `;

  const printWindow = window.open('', '_blank');
  if (printWindow) {
    printWindow.document.open();
    printWindow.document.write(printHtml);
    printWindow.document.close();
  } else {
    alert("Please allow popups to export the PDF.");
  }
}

function escapeHtml(text) {
  if (!text) return '';
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
