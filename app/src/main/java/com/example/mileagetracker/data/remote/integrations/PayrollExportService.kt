package com.example.mileagetracker.data.remote.integrations

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.mileagetracker.util.DateFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a Canadian-payroll-compatible CSV (Wagepoint / Payworks style) for
 * approved mileage reimbursements. The file is written to the app's external
 * cache directory and exposed via FileProvider for sharing.
 */
@Singleton
class PayrollExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class PayrollRow(
        val employeeName: String,
        val periodStart: String, // YYYY-MM-DD
        val periodEnd: String, // YYYY-MM-DD
        val totalKm: Double,
        val reimbursementAmountCad: Double,
        val policyRateCentsPerKm: Int
    )

    /**
     * Writes [rows] to a CSV file and returns a shareable [Uri].
     *
     * CSV header: Employee Name, Period Start, Period End,
     *             Total KM, Rate (¢/km), Reimbursement (CAD)
     */
    fun generateCsv(rows: List<PayrollRow>): Uri {
        val fileName = "payroll_export_${DateFormats.exportFileTimestampNow()}.csv"
        val file = File(context.externalCacheDir, fileName)

        FileWriter(file).use { writer ->
            writer.appendLine("Employee Name,Period Start,Period End,Total KM,Rate (cents/km),Reimbursement (CAD)")
            rows.forEach { row ->
                writer.appendLine(
                    "\"${row.employeeName}\"," +
                        "${row.periodStart}," +
                        "${row.periodEnd}," +
                        "${"%.2f".format(row.totalKm)}," +
                        "${row.policyRateCentsPerKm}," +
                        "%.2f".format(row.reimbursementAmountCad)
                )
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }
}
