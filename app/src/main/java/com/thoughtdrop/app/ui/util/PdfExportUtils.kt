package com.thoughtdrop.app.ui.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.thoughtdrop.app.data.model.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtils {

    /**
     * Exports and prints a high-fidelity, executive-formatted PDF of a NoteEntity
     * using the Android Print Spooler with full A4 vector layout, rich styling,
     * and crisp bullet points.
     */
    fun exportNoteAsPdf(context: Context, note: NoteEntity) {
        val webView = WebView(context)
        val cleanTitle = note.title.ifBlank { "ThoughtDrop_Note_${note.id}" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(40)

        val htmlContent = generateExecutiveHtml(note)

        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("ThoughtDrop_$cleanTitle")
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

                printManager?.print("ThoughtDrop - $cleanTitle", printAdapter, printAttributes)
            }
        }

        webView.loadDataWithBaseURL(null, htmlContent, "text/html; charset=UTF-8", "UTF-8", null)
    }

    private fun generateExecutiveHtml(note: NoteEntity): String {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault())
        val dateString = dateFormat.format(Date(note.createdAt))

        val keyPoints = note.getKeyPoints()
        val actionItems = note.getActionItems()

        // Crimson Red & Ruby Theme (matching mayankmanjul.com HUD Crimson)
        val crimsonPrimary = "#ff3366"
        val crimsonRuby = "#e11d48"
        val crimsonBgTint = "#fff1f3"
        val crimsonBorder = "#fecdd3"

        val keyPointsHtml = if (keyPoints.isNotEmpty()) {
            """
            <div class="section">
                <div class="section-title">
                    <span class="icon">✦</span> KEY INSIGHTS & CORE DETAILS
                </div>
                <ul class="bullet-list">
                    ${keyPoints.joinToString("") { "<li><span class=\"bullet\">•</span><span class=\"bullet-text\">${escapeHtml(it)}</span></li>" }}
                </ul>
            </div>
            """.trimIndent()
        } else ""

        val actionItemsHtml = if (actionItems.isNotEmpty()) {
            """
            <div class="section">
                <div class="section-title">
                    <span class="icon">✦</span> ACTION ITEMS & NEXT STEPS
                </div>
                <ul class="bullet-list">
                    ${actionItems.joinToString("") { item ->
                        "<li><span class=\"bullet\">•</span><span class=\"bullet-text\">${escapeHtml(item)}</span></li>"
                    }}
                </ul>
            </div>
            """.trimIndent()
        } else ""

        val transcriptHtml = if (note.transcript.isNotBlank()) {
            """
            <div class="section">
                <div class="section-title">
                    <span class="icon">🎙</span> ORIGINAL RECORDING TRANSCRIPT
                </div>
                <div class="transcript-box">
                    ${escapeHtml(note.transcript)}
                </div>
            </div>
            """.trimIndent()
        } else ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>${escapeHtml(note.title)}</title>
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
                    border-bottom: 2px solid $crimsonPrimary;
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
                    color: $crimsonPrimary;
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
                    color: $crimsonRuby;
                    background: $crimsonBgTint;
                    border: 1px solid $crimsonBorder;
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
                    border-left: 4px solid $crimsonPrimary;
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
                    color: $crimsonPrimary;
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
                    color: $crimsonPrimary;
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
                    color: $crimsonRuby;
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
                    <span class="category-badge">${note.category.uppercase()}</span>
                    <div class="date-text">$dateString</div>
                </div>
            </div>

            <h1 class="title">${escapeHtml(note.title.ifBlank { "Untitled Note" })}</h1>

            <div class="summary-card">
                ${escapeHtml(note.summary.ifBlank { "No summary provided." })}
            </div>

            $keyPointsHtml

            $actionItemsHtml

            $transcriptHtml

            <div class="footer">
                <span class="footer-brand">ThoughtDrop • Personal & Confidential</span>
                <span>Exported via AI Note Studio</span>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
