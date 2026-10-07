package com.example.mileagetracker.domain.model

/**
 * Supported logbook export formats.
 *
 * Used as a discriminator in [LogbookExportRequest] and stored as its
 * [name] string in [ReportHistoryEntity.format] so that display labels
 * can be derived without a separate lookup table.
 *
 * @property mimeType  The IANA MIME type string for this format, used when
 *                     building `Intent.ACTION_SEND` share intents so the
 *                     Android share sheet filters to compatible receiver apps.
 */
enum class ExportFormat(val mimeType: String) {
    /** Multi-page PDF rendered with Android [android.graphics.pdf.PdfDocument]. */
    PDF(mimeType = "application/pdf"),

    /** UTF-8 CSV with CRA-aligned column headings. */
    CSV(mimeType = "text/csv"),
}
