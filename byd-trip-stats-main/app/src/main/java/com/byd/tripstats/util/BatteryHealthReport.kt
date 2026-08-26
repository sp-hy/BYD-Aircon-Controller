package com.byd.tripstats.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a **battery health report** — as HTML or as a native PDF — and saves it to
 * Download/BydTripStats/ via MediaStore. Intended as evidence for resale listings or
 * warranty disputes: the PDF prints/attaches directly; the HTML opens in any browser.
 *
 * It is a Pro feature; the caller ([com.byd.tripstats.ui.screens.BatteryDegradationScreen])
 * gates it on [com.byd.tripstats.data.entitlement.EntitlementManager]. This util only
 * renders + writes; it does not check entitlement itself.
 *
 * Writes mirror [ScreenshotUtil]'s MediaStore pattern (pending entry → write → publish,
 * with rollback on failure). On the BYD DiLink head unit the public Download directory is
 * "Download"; RELATIVE_PATH is relative to that root.
 */
object BatteryHealthReport {
    private const val TAG = "BatteryHealthReport"
    private const val REPORT_DIR = "Download/BydTripStats"

    /** One recorded SoH sample, already formatted for display. */
    data class Entry(val date: String, val odometerKm: Double, val soh: Double)

    /** Everything needed to render the report — computed by the caller. */
    data class Data(
        val vehicleName: String,
        val batteryKwh: Double?,
        val appVersion: String,
        val currentSoh: Double,
        val declinePerYearLabel: String,
        val projectedAt80Label: String,
        val firstDate: String,
        val lastDate: String,
        val tripsAnalyzed: Int,
        val warrantyNote: String,
        val exclusionNote: String? = null,
        val entries: List<Entry>
    )

    /**
     * Builds the HTML report and writes it to Download/BydTripStats/. Must be called from a
     * coroutine. Returns the saved file name; throws on failure so the caller can surface it.
     */
    suspend fun generateAndSave(context: Context, data: Data): String =
        withContext(Dispatchers.IO) {
            val html = buildHtml(data)
            val fileName = "byd_battery_health_${timestamp()}.html"
            saveToDownloads(context, fileName, "text/html") { it.write(html.toByteArray(Charsets.UTF_8)) }
                .also { Log.i(TAG, "Battery health report saved: $REPORT_DIR/$it") }
        }

    /**
     * Builds a native PDF report and writes it to Download/BydTripStats/. Must be called from
     * a coroutine. Returns the saved file name; throws on failure.
     */
    suspend fun generatePdfAndSave(context: Context, data: Data): String =
        withContext(Dispatchers.IO) {
            val doc = renderPdf(data)
            val fileName = "byd_battery_health_${timestamp()}.pdf"
            try {
                saveToDownloads(context, fileName, "application/pdf") { doc.writeTo(it) }
                    .also { Log.i(TAG, "Battery health PDF saved: $REPORT_DIR/$it") }
            } finally {
                doc.close()
            }
        }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

    /** Shared MediaStore write: create pending entry → [writer] → publish, rollback on error. */
    private fun saveToDownloads(
        context: Context,
        fileName: String,
        mime: String,
        writer: (OutputStream) -> Unit
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, REPORT_DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create report file in Download")

        try {
            resolver.openOutputStream(uri)?.use { writer(it) }
                ?: throw IOException("Could not open output stream")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return fileName
    }

    // ── PDF rendering ───────────────────────────────────────────────────────────

    private const val PAGE_W = 595   // A4 @ 72 dpi, points
    private const val PAGE_H = 842

    private fun renderPdf(d: Data): PdfDocument {
        val doc = PdfDocument()
        val marginL = 40f
        val marginTop = 40f
        val marginBottom = 44f
        val contentW = PAGE_W - marginL * 2

        val cInk = 0xFF1A1A1A.toInt()
        val cMuted = 0xFF666666.toInt()
        val cAccent = 0xFF2196F3.toInt()
        val cLine = 0xFFE0E0E0.toInt()

        val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true; color = cInk }
        val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = cMuted }
        val pH2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true; color = cInk }
        val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = cMuted }
        val pValue = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; isFakeBoldText = true; color = cInk }
        val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF333333.toInt() }
        val pHead = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; isFakeBoldText = true; color = cMuted }
        val pCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = cInk }
        val pRule = Paint().apply { color = cLine; strokeWidth = 1f }
        val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = cLine; strokeWidth = 1f }
        val pNoteBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F8FF.toInt() }
        val pAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cAccent }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = marginTop

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = marginTop
        }

        // Draw one baseline of text, advancing y by the line height first.
        fun line(s: String, p: Paint, x: Float = marginL) {
            y += p.fontSpacing
            canvas.drawText(s, x, y, p)
        }

        fun wrapped(s: String, p: Paint, x: Float, maxW: Float) {
            for (l in wrap(s, p, maxW)) line(l, p, x)
        }

        // Header
        line("Battery Health Report", pTitle)
        y += 4f
        val battery = d.batteryKwh?.let { "%.1f kWh".format(it) } ?: "—"
        wrapped(
            "${d.vehicleName}  ·  Battery $battery  ·  Generated " +
                "${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}  ·  " +
                "BYD Trip Stats ${d.appVersion}",
            pSub, marginL, contentW
        )

        // Summary cards (2 × 2)
        y += 14f
        val gap = 12f
        val cardW = (contentW - gap) / 2f
        val cardH = 52f
        fun statCard(left: Float, top: Float, label: String, value: String) {
            canvas.drawRoundRect(RectF(left, top, left + cardW, top + cardH), 8f, 8f, pBorder)
            canvas.drawText(label.uppercase(Locale.getDefault()), left + 12f, top + 19f, pLabel)
            canvas.drawText(value, left + 12f, top + 42f, pValue)
        }
        statCard(marginL, y, "Current State of Health", "%.1f%%".format(d.currentSoh))
        statCard(marginL + cardW + gap, y, "Trips analysed", "${d.tripsAnalyzed}")
        y += cardH + gap
        statCard(marginL, y, "Decline rate", d.declinePerYearLabel)
        statCard(marginL + cardW + gap, y, "Projected to 80%", d.projectedAt80Label)
        y += cardH

        // Warranty note box
        y += 16f
        val noteLines = wrap(d.warrantyNote, pBody, contentW - 24f)
        val noteH = noteLines.size * pBody.fontSpacing + 16f
        canvas.drawRoundRect(RectF(marginL, y, marginL + contentW, y + noteH), 4f, 4f, pNoteBg)
        canvas.drawRect(RectF(marginL, y, marginL + 3f, y + noteH), pAccent)
        var noteY = y + 8f
        for (l in noteLines) { noteY += pBody.fontSpacing; canvas.drawText(l, marginL + 14f, noteY, pBody) }
        y += noteH

        // Measurement period
        y += 18f
        line("Measurement period", pH2)
        y += 2f
        line("${d.firstDate} – ${d.lastDate}", pSub)
        d.exclusionNote?.let { wrapped(it, pSub, marginL, contentW) }

        // SoH history table
        y += 18f
        line("Recorded SoH history", pH2)
        val colDate = marginL
        val colOdo = marginL + contentW * 0.46f
        val colSoh = marginL + contentW * 0.74f
        fun tableHeader() {
            y += 6f
            line("DATE", pHead, colDate)
            canvas.drawText("ODOMETER", colOdo, y, pHead)
            canvas.drawText("STATE OF HEALTH", colSoh, y, pHead)
            y += 4f
            canvas.drawLine(marginL, y, marginL + contentW, y, pRule)
        }
        tableHeader()
        for (e in d.entries) {
            if (y + pCell.fontSpacing > PAGE_H - marginBottom - 70f) {
                newPage()
                tableHeader()
            }
            y += pCell.fontSpacing
            canvas.drawText(e.date, colDate, y, pCell)
            canvas.drawText("%,.0f km".format(e.odometerKm), colOdo, y, pCell)
            canvas.drawText("%.1f%%".format(e.soh), colSoh, y, pCell)
        }

        // Footer disclaimer
        val disclaimer = "This report was generated on-device from the vehicle's own telemetry by the " +
            "BYD Trip Stats app. State of Health (SoH) is read directly from the battery management " +
            "system where the vehicle exposes it, otherwise it is estimated from telemetry and may " +
            "differ from a dealer diagnostic. The decline rate and 80% projection are a least-squares " +
            "linear fit over the recorded trips and are indicative, not a guarantee. No data left the " +
            "vehicle to produce this report."
        val footLines = wrap(disclaimer, pSub, contentW)
        val footNeeded = footLines.size * pSub.fontSpacing + 24f
        if (y + footNeeded > PAGE_H - marginBottom) newPage()
        y += 18f
        canvas.drawLine(marginL, y, marginL + contentW, y, pRule)
        y += 6f
        wrapped(disclaimer, pSub, marginL, contentW)

        doc.finishPage(page)
        return doc
    }

    /** Greedy word-wrap of [text] to [maxW] using [paint]'s metrics. */
    private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(candidate) <= maxW) {
                cur = StringBuilder(candidate)
            } else {
                if (cur.isNotEmpty()) lines.add(cur.toString())
                cur = StringBuilder(word)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    // ── HTML rendering ──────────────────────────────────────────────────────────

    private fun buildHtml(d: Data): String {
        val generated = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        val battery = d.batteryKwh?.let { "%.1f kWh".format(it) } ?: "—"
        val rows = d.entries.joinToString("\n") { e ->
            "<tr><td>${esc(e.date)}</td><td>${"%,.0f".format(e.odometerKm)} km</td>" +
                "<td>${"%.1f".format(e.soh)}%</td></tr>"
        }
        return """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Battery Health Report — ${esc(d.vehicleName)}</title>
<style>
  :root { --blue:#2196F3; --ink:#1a1a1a; --muted:#666; --line:#e0e0e0; }
  * { box-sizing: border-box; }
  body { font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
         color: var(--ink); margin: 0; padding: 32px; max-width: 820px; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  .sub { color: var(--muted); font-size: 13px; margin-bottom: 24px; }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 20px 0; }
  .card { border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; }
  .card .label { color: var(--muted); font-size: 12px; text-transform: uppercase;
                 letter-spacing: .04em; }
  .card .value { font-size: 22px; font-weight: 700; margin-top: 4px; }
  table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 14px; }
  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); }
  th { color: var(--muted); font-weight: 600; font-size: 12px; text-transform: uppercase; }
  .note { background: #f5f8ff; border-left: 3px solid var(--blue); padding: 12px 16px;
          border-radius: 4px; font-size: 13px; color: #234; margin: 20px 0; }
  .foot { color: var(--muted); font-size: 11px; margin-top: 28px; line-height: 1.5; }
  h2 { font-size: 15px; margin: 28px 0 4px; }
  @media print { body { padding: 0; } }
</style></head>
<body>
  <h1>Battery Health Report</h1>
  <div class="sub">${esc(d.vehicleName)} &middot; Battery $battery &middot; Generated $generated
    &middot; BYD Trip Stats ${esc(d.appVersion)}</div>

  <div class="grid">
    <div class="card"><div class="label">Current State of Health</div>
      <div class="value">${"%.1f".format(d.currentSoh)}%</div></div>
    <div class="card"><div class="label">Trips Analysed</div>
      <div class="value">${d.tripsAnalyzed}</div></div>
    <div class="card"><div class="label">Decline Rate</div>
      <div class="value">${esc(d.declinePerYearLabel)}</div></div>
    <div class="card"><div class="label">Projected to 80%</div>
      <div class="value">${esc(d.projectedAt80Label)}</div></div>
  </div>

  <div class="note">${esc(d.warrantyNote)}</div>

  <h2>Measurement period</h2>
  <div class="sub">${esc(d.firstDate)} – ${esc(d.lastDate)}</div>
  ${d.exclusionNote?.let { "<div class=\"sub\">${esc(it)}</div>" } ?: ""}

  <h2>Recorded SoH history</h2>
  <table>
    <thead><tr><th>Date</th><th>Odometer</th><th>State of Health</th></tr></thead>
    <tbody>
$rows
    </tbody>
  </table>

  <p class="foot">
    This report was generated on-device from the vehicle's own telemetry by the BYD Trip
    Stats app. State of Health (SoH) is read directly from the battery management system
    where the vehicle exposes it, otherwise it is estimated from telemetry and may differ
    from a dealer diagnostic. The decline rate and 80% projection are a least-squares linear
    fit over the recorded trips and are indicative, not a guarantee. No data left the vehicle
    to produce this report.
  </p>
</body></html>"""
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
