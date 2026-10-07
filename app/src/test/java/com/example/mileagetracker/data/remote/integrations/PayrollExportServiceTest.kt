package com.example.mileagetracker.data.remote.integrations

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PayrollExportServiceTest {

    private lateinit var context: Context
    private lateinit var service: PayrollExportService
    private val createdFiles = mutableListOf<File>()
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = PayrollExportService(context)
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.CANADA)

        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } answers {
            val file = thirdArg<File>()
            createdFiles += file
            Uri.fromFile(file)
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(FileProvider::class)
        createdFiles.forEach { it.delete() }
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `generateCsv with empty rows writes header only`() {
        val uri = service.generateCsv(emptyList())
        val lines = readLines(uri)

        assertEquals(1, lines.size)
        assertEquals(
            "Employee Name,Period Start,Period End,Total KM,Rate (cents/km),Reimbursement (CAD)",
            lines[0],
        )
    }

    @Test
    fun `generateCsv with one row writes header and one data line`() {
        val uri = service.generateCsv(listOf(row("Alice", 100.0, 70.0, 70)))
        val lines = readLines(uri)

        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"Alice\""))
    }

    @Test
    fun `generateCsv formats total km with two decimal places`() {
        val uri = service.generateCsv(listOf(row("A", 12.345, 1.0, 1)))
        val lines = readLines(uri)

        assertTrue(lines[1].contains(",12.35,"))
    }

    @Test
    fun `generateCsv formats reimbursement with two decimal places`() {
        val uri = service.generateCsv(listOf(row("A", 1.0, 9.876, 1)))
        val lines = readLines(uri)

        assertTrue(lines[1].endsWith(",9.88"))
    }

    @Test
    fun `generateCsv quotes employee name field`() {
        val uri = service.generateCsv(listOf(row("Bob Smith", 1.0, 2.0, 3)))
        val lines = readLines(uri)

        assertTrue(lines[1].startsWith("\"Bob Smith\","))
    }

    @Test
    fun `generateCsv preserves row order`() {
        val uri = service.generateCsv(
            listOf(
                row("First", 1.0, 1.0, 1),
                row("Second", 2.0, 2.0, 2),
            )
        )
        val lines = readLines(uri)

        assertTrue(lines[1].startsWith("\"First\","))
        assertTrue(lines[2].startsWith("\"Second\","))
    }

    @Test
    fun `generateCsv sends package authority to file provider`() {
        service.generateCsv(listOf(row("A", 1.0, 1.0, 1)))

        verify(exactly = 1) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                any(),
            )
        }
    }

    @Test
    fun `generateCsv writes rate cents as integer`() {
        val uri = service.generateCsv(listOf(row("A", 1.0, 1.0, 73)))
        val lines = readLines(uri)

        assertTrue(lines[1].contains(",73,"))
    }

    @Test
    fun `generateCsv returns uri produced by file provider`() {
        val expected = mockk<Uri>()
        every { FileProvider.getUriForFile(any(), any(), any()) } returns expected

        val result = service.generateCsv(listOf(row("A", 1.0, 1.0, 1)))

        assertEquals(expected, result)
    }

    @Test
    fun `generateCsv creates payroll export csv filename`() {
        service.generateCsv(listOf(row("A", 1.0, 1.0, 1)))
        val file = createdFiles.last()

        assertTrue(file.name.startsWith("payroll_export_"))
        assertTrue(file.name.endsWith(".csv"))
    }

    private fun row(
        employee: String,
        totalKm: Double,
        reimbursement: Double,
        rateCents: Int,
    ) = PayrollExportService.PayrollRow(
        employeeName = employee,
        periodStart = "2026-01-01",
        periodEnd = "2026-01-31",
        totalKm = totalKm,
        reimbursementAmountCad = reimbursement,
        policyRateCentsPerKm = rateCents,
    )

    private fun readLines(uri: Uri): List<String> {
        val file = File(requireNotNull(uri.path))
        return file.readLines()
    }
}
