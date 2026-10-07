package com.example.mileagetracker.data.remote.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.example.mileagetracker.domain.model.OcrExtractionResult
import com.example.mileagetracker.domain.service.OcrService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * On-device receipt OCR backed by **Google ML Kit Text Recognition v2**.
 *
 * Implements [OcrService] so use-cases in the domain layer never import
 * anything from `data`.  The concrete binding is declared in
 * `ExpenseModule` (Hilt) and can be replaced by a cloud provider without
 * touching any use-case or UI code.
 *
 * ## Camera URI lifecycle
 * Call [createReceiptImageUri] before launching the `TakePicture` camera
 * contract.  It creates an empty JPEG under `<cacheDir>/ocr_receipts/`
 * and returns a `content://` URI via the app's FileProvider
 * (authority `${applicationId}.provider`, path declared in
 * `res/xml/file_paths.xml` as `<cache-path name="ocr_receipts" .../>`).
 * The camera writes the captured image into that file.  Pass the same URI
 * to [extractFromImage] once the contract returns `true`.
 *
 * ## Extraction heuristics
 * - **Total / amount** — lines matching "total", "amount due", "balance due",
 *   or "subtotal" followed by a CAD dollar figure.
 * - **GST/HST** — lines containing "gst", "hst", or "tax" followed by a
 *   dollar figure.
 * - **Vendor name** — first non-empty, non-date, non-dollar line.
 * - **Date** — first line matching common Canadian date formats.
 *
 * All fields are best-effort; null means the user must fill in manually.
 */
@Singleton
class MlKitOcrService @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OcrService {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // FileProvider authority matches AndroidManifest.xml
    private val authority = "${context.packageName}.provider"

    // ── Camera URI ──────────────────────────────────────────────────────────

    /**
     * Creates an empty JPEG file under `<cacheDir>/ocr_receipts/` and returns
     * its FileProvider `content://` URI, ready to be passed to an
     * `ActivityResultContracts.TakePicture` launcher.
     *
     * Must be called on the **main thread** before launching the camera intent.
     */
    fun createReceiptImageUri(): Uri {
        val dir = File(context.cacheDir, "ocr_receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${UUID.randomUUID()}.jpg")
        file.createNewFile()
        return FileProvider.getUriForFile(context, authority, file)
    }

    // ── OcrService impl ────────────────────────────────────────────────────────

    override suspend fun extractFromImage(imageUri: Uri): OcrExtractionResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val image = InputImage.fromFilePath(context, imageUri)
                val rawText = recognizeText(image)
                parseReceiptText(rawText)
            }.getOrElse {
                OcrExtractionResult.EMPTY
            }
        }

    // ── ML Kit bridge (callback → coroutine) ──────────────────────────────────

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { _ -> cont.resume("") }
        }

    // ── Date formatters ─────────────────────────────────────────────────────────

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MMM d yyyy"),
        DateTimeFormatter.ofPattern("MMMM d yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("d MMMM yyyy"),
    )

    // ── Regex helpers ─────────────────────────────────────────────────────────

    private val dollarRegex = Regex("""\$?\s*(\d{1,6}[.,]\d{2})""")
    private val totalKeywords = Regex("""total|amount\s+due|balance\s+due|subtotal""", RegexOption.IGNORE_CASE)
    private val primaryTotalKeywords = Regex("""\btotal\b|amount\s+due|balance\s+due""", RegexOption.IGNORE_CASE)
    private val taxKeywords = Regex("""\bgst\b|\bhst\b|\btax\b""", RegexOption.IGNORE_CASE)

    // ── Receipt parser ────────────────────────────────────────────────────────

    @androidx.annotation.VisibleForTesting
    internal fun parseReceiptText(rawText: String): OcrExtractionResult {
        if (rawText.isBlank()) return OcrExtractionResult.EMPTY

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var detectedAmount: Double? = null
        var detectedGstHst: Double? = null
        var detectedVendor: String? = null
        var detectedDate: Long? = null

        for (line in lines) {
            if (totalKeywords.containsMatchIn(line)) {
                val amount = dollarRegex.find(line)
                    ?.groupValues?.get(1)
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                val shouldOverride = detectedAmount == null || primaryTotalKeywords.containsMatchIn(line)
                if (amount != null && shouldOverride) {
                    detectedAmount = amount
                }
            }
            if (detectedGstHst == null && taxKeywords.containsMatchIn(line)) {
                detectedGstHst = dollarRegex.find(line)
                    ?.groupValues?.get(1)
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
            }
            if (detectedDate == null) {
                detectedDate = tryParseDate(line)
            }
        }

        detectedVendor = lines.firstOrNull { line ->
            tryParseDate(line) == null &&
                !dollarRegex.matches(line) &&
                line.length in 2..60
        }

        val fieldsFound = listOf(detectedAmount, detectedGstHst, detectedVendor, detectedDate)
            .count { it != null }
        val confidence = (fieldsFound / 4f).coerceIn(0f, 1f)

        return OcrExtractionResult(
            rawText = rawText,
            detectedAmount = detectedAmount,
            detectedVendor = detectedVendor,
            detectedDate = detectedDate,
            detectedGstHst = detectedGstHst,
            confidence = confidence,
        )
    }

    private fun tryParseDate(line: String): Long? {
        val cleaned = line.replace(Regex("[,/\\-]"), " ").replace(Regex("\\s+"), " ").trim()
        for (fmt in dateFormatters) {
            runCatching {
                LocalDate.parse(cleaned, fmt)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.onSuccess { return it }
        }
        return null
    }
}
