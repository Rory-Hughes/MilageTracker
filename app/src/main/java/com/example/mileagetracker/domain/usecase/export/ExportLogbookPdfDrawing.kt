package com.example.mileagetracker.domain.usecase.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.mileagetracker.data.formatting.CanadianAddressFormatter

internal const val PDF_PAGE_WIDTH = 595
internal const val PDF_PAGE_HEIGHT = 842
internal const val PDF_MARGIN_LEFT = 24
internal const val PDF_ROW_HEIGHT = 20
internal const val PDF_FIRST_SECTION_PAGE_ROWS = 24
internal const val PDF_CONTINUATION_ROWS = 31
internal const val PDF_CONTINUATION_TABLE_START_Y = 84

internal val PDF_COLS_WITH_ROUTE = listOf(
    "Date" to 72,
    "Start" to 165,
    "End" to 165,
    "Category" to 80,
    "km" to 50,
)

internal val PDF_COLS_NO_ROUTE = listOf(
    "Date" to 80,
    "Destination" to 360,
    "Category" to 90,
    "km" to 65,
)

internal fun renderContinuationHeader(
    canvas: Canvas,
    label: String,
) {
    val paint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 16f
        color = Color.BLACK
    }
    canvas.drawText("$label (continued)", PDF_MARGIN_LEFT.toFloat(), 60f, paint)
}

internal fun renderTripTableHeader(
    canvas: Canvas,
    top: Int,
    includeRoute: Boolean,
) {
    val headerPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 11f
        color = Color.WHITE
    }
    val bgPaint = Paint().apply { color = Color.rgb(30, 80, 160) }
    canvas.drawRect(
        0f,
        top.toFloat(),
        PDF_PAGE_WIDTH.toFloat(),
        (top + PDF_ROW_HEIGHT).toFloat(),
        bgPaint,
    )

    var x = PDF_MARGIN_LEFT.toFloat()
    val columns = if (includeRoute) PDF_COLS_WITH_ROUTE else PDF_COLS_NO_ROUTE
    columns.forEach { (label, width) ->
        canvas.drawText(label, x + 4f, (top + 14).toFloat(), headerPaint)
        x += width
    }
}

internal fun renderTripRow(
    canvas: Canvas,
    y: Float,
    date: String,
    startLocation: String,
    endLocation: String,
    destination: String,
    category: String,
    km: String,
    includeRoute: Boolean,
    isEvenRow: Boolean,
) {
    val bgPaint = Paint().apply {
        color = if (isEvenRow) Color.WHITE else Color.rgb(240, 244, 252)
    }
    canvas.drawRect(0f, y, PDF_PAGE_WIDTH.toFloat(), y + PDF_ROW_HEIGHT, bgPaint)

    val cellPaint = Paint().apply {
        textSize = 10f
        color = Color.DKGRAY
    }
    val columns = if (includeRoute) PDF_COLS_WITH_ROUTE else PDF_COLS_NO_ROUTE
    val values = if (includeRoute) {
        listOf(date, startLocation, endLocation, category, km)
    } else {
        listOf(date, destination, category, km)
    }

    var x = PDF_MARGIN_LEFT.toFloat()
    columns.zip(values).forEach { (col, value) ->
        canvas.drawText(value.take((col.second / 6).coerceAtLeast(4)), x + 4f, y + 14f, cellPaint)
        x += col.second
    }
}

internal fun renderEmptySectionRow(
    canvas: Canvas,
    y: Int,
) {
    val paint = Paint().apply {
        textSize = 11f
        color = Color.DKGRAY
    }
    canvas.drawText("No qualifying trips for this section.", PDF_MARGIN_LEFT.toFloat(), y.toFloat(), paint)
}

internal fun renderSectionFooter(
    canvas: Canvas,
    prepared: PreparedLogbookExport,
    totals: PreparedLogbookTotals,
    y: Float,
) {
    val dividerPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
    }
    val footerPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 11f
        color = Color.BLACK
    }
    canvas.drawLine(
        PDF_MARGIN_LEFT.toFloat(),
        y,
        (PDF_PAGE_WIDTH - PDF_MARGIN_LEFT).toFloat(),
        y,
        dividerPaint,
    )
    canvas.drawText(
        buildPdfFooterText(prepared, totals),
        PDF_MARGIN_LEFT.toFloat(),
        y + 18f,
        footerPaint,
    )
}

internal fun drawWrappedText(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    maxChars: Int,
    lineHeight: Float,
    paint: Paint,
) {
    var currentY = y
    text.chunked(maxChars).forEach { line ->
        canvas.drawText(line, x, currentY, paint)
        currentY += lineHeight
    }
}

internal fun pdfNormalizedLocation(location: String): String =
    CanadianAddressFormatter.normalizeRawLocationForExport(location)

internal fun pdfRouteLocation(includeRouteDetails: Boolean, location: String): String =
    if (includeRouteDetails) pdfNormalizedLocation(location) else ""

private fun buildPdfFooterText(
    prepared: PreparedLogbookExport,
    totals: PreparedLogbookTotals,
): String =
    "${prepared.distanceLabel}: ${"%.2f".format(totals.selectedKm)} km | " +
        "Total logged km: ${"%.2f".format(totals.totalKm)} km | " +
        "${prepared.percentLabel}: ${"%.2f%%".format(totals.selectedUsePercent)}"
