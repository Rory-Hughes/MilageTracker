package com.example.mileagetracker.domain.usecase.export

import android.content.Context
import android.net.Uri
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ExportLogbookToCsvUseCaseTest {

    private lateinit var buildLogbookExportData: BuildLogbookExportDataUseCase
    private lateinit var context: Context
    private lateinit var useCase: ExportLogbookToCsvUseCase
    private val tempDir = File("build/tmp/test_csv_export").apply { mkdirs() }

    @Before
    fun setUp() {
        buildLogbookExportData = mockk()
        context = mockk()
        every { context.cacheDir } returns tempDir
        useCase = ExportLogbookToCsvUseCase(buildLogbookExportData, context)
        mockkStatic(Uri::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun `generate writes grouped all vehicle csv with subtotals and totals`() = runTest {
        val request = LogbookExportRequest(
            vehicleId = null,
            taxYear = 2025,
            format = ExportFormat.CSV,
            categories = setOf(TripCategory.BUSINESS),
        )
        coEvery { buildLogbookExportData(request) } returns preparedExport(
            scopeLabel = "All vehicles",
            scopeToken = "all_vehicles",
            sections = listOf(
                section(
                    label = "Sedan",
                    trips = listOf(
                        trip(
                            startedAt = 1_735_689_600_000L,
                            endLocation = "Alpha",
                            distanceKm = 5.0,
                            vehicleLabel = "Sedan",
                        ),
                    ),
                    selectedKm = 5.0,
                    totalKm = 10.0,
                    selectedUsePercent = 50.0,
                ),
                section(
                    label = "Unassigned",
                    trips = listOf(
                        trip(
                            startedAt = 1_735_776_000_000L,
                            endLocation = "Beta",
                            distanceKm = 3.0,
                            vehicleLabel = "Unassigned",
                        ),
                    ),
                    selectedKm = 3.0,
                    totalKm = 3.0,
                    selectedUsePercent = 100.0,
                ),
            ),
            selectedKm = 8.0,
            totalKm = 13.0,
            selectedUsePercent = 61.538,
        )
        every { Uri.fromFile(any()) } answers { mockk(relaxed = true) }

        val result = useCase(request)

        assertTrue(result is ExportResult.Success)
        val file = File(tempDir, "exports").listFiles()!!.single()
        assertTrue(file.name.startsWith("logbook_all_vehicles_2025_"))
        assertGroupedAllVehicleCsv(file.readText())
    }

    @Test
    fun `generate keeps columns stable when route details are disabled`() = runTest {
        val request = LogbookExportRequest(
            vehicleId = 1,
            taxYear = 2025,
            format = ExportFormat.CSV,
            categories = setOf(TripCategory.BUSINESS),
            includeRouteDetails = false,
        )
        coEvery { buildLogbookExportData(request) } returns preparedExport(
            scopeLabel = "Sedan",
            scopeToken = "vehicle_1",
            includeRouteDetails = false,
            sections = listOf(
                section(
                    label = "Sedan",
                    trips = listOf(
                        trip(
                            startedAt = 1_735_689_600_000L,
                            startLocation = "Home",
                            endLocation = "Office",
                            vehicleLabel = "Sedan",
                        ),
                    ),
                    selectedKm = 4.0,
                    totalKm = 4.0,
                    selectedUsePercent = 100.0,
                )
            ),
            selectedKm = 4.0,
            totalKm = 4.0,
            selectedUsePercent = 100.0,
        )
        every { Uri.fromFile(any()) } answers { mockk(relaxed = true) }

        useCase(request)

        val file = File(tempDir, "exports").listFiles()!!.single()
        val dataLine = file.readLines().first { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) }
        val columns = dataLine.split(",")
        assertEquals(8, columns.size)
        assertEquals("", columns[1])
        assertEquals("", columns[2])
        assertEquals("Office", columns[3])
        assertEquals("Sedan", columns[6])
    }

    private fun assertGroupedAllVehicleCsv(content: String) {
        assertTrue(
            content.contains(
                "Date,Start Location,End Location,Destination,Category,Distance (km),Vehicle,Notes",
            ),
        )
        assertTrue(content.contains(",Sedan,"))
        assertTrue(content.contains(",Unassigned,"))
        assertTrue(content.contains("SUBTOTAL,,,,,5.00,Sedan,"))
        assertTrue(content.contains("SUBTOTAL,,,,,3.00,Unassigned,"))
        assertTrue(content.contains("TOTALS,,,,,8.00,All vehicles,"))
        assertTrue(content.contains("# Scope: All vehicles"))
        assertTrue(content.contains("# Selected categories: Business"))
    }

    @Test
    fun `generate uses unique timestamped filenames`() = runTest {
        val request = LogbookExportRequest(
            vehicleId = 1,
            taxYear = 2025,
            format = ExportFormat.CSV,
        )
        coEvery { buildLogbookExportData(request) } returns preparedExport(
            scopeLabel = "Sedan",
            scopeToken = "vehicle_1",
            sections = emptyList(),
            selectedKm = 0.0,
            totalKm = 0.0,
            selectedUsePercent = 0.0,
        )
        every { Uri.fromFile(any()) } answers { mockk(relaxed = true) }

        useCase(request)
        Thread.sleep(2)
        useCase(request)

        val names = File(tempDir, "exports").listFiles()!!.map { it.name }
        assertEquals(2, names.size)
        assertNotEquals(names[0], names[1])
        assertTrue(names.all { it.startsWith("logbook_vehicle_1_2025_") })
    }

    private fun preparedExport(
        scopeLabel: String,
        scopeToken: String,
        includeRouteDetails: Boolean = true,
        sections: List<PreparedLogbookSection>,
        selectedKm: Double,
        totalKm: Double,
        selectedUsePercent: Double,
    ) = PreparedLogbookExport(
        vehicleId = if (scopeToken == "all_vehicles") null else 1,
        taxYear = 2025,
        categories = listOf(TripCategory.BUSINESS),
        includeRouteDetails = includeRouteDetails,
        scopeLabel = scopeLabel,
        scopeToken = scopeToken,
        sections = sections,
        totals = PreparedLogbookTotals(
            totalTripCount = sections.sumOf { it.totals.totalTripCount },
            exportedTripCount = sections.sumOf { it.totals.exportedTripCount },
            totalKm = totalKm,
            selectedKm = selectedKm,
            selectedUsePercent = selectedUsePercent,
        ),
    )

    private fun section(
        label: String,
        trips: List<PreparedLogbookTrip>,
        selectedKm: Double,
        totalKm: Double,
        selectedUsePercent: Double,
    ) = PreparedLogbookSection(
        vehicleId = if (label == "Unassigned") null else 1,
        vehicle = null,
        label = label,
        trips = trips,
        totals = PreparedLogbookTotals(
            totalTripCount = if (totalKm > 0.0) maxOf(1, trips.size) else 0,
            exportedTripCount = trips.size,
            totalKm = totalKm,
            selectedKm = selectedKm,
            selectedUsePercent = selectedUsePercent,
        ),
    )

    private fun trip(
        startedAt: Long,
        startLocation: String = "Home",
        endLocation: String,
        distanceKm: Double = 4.0,
        vehicleLabel: String,
    ) = PreparedLogbookTrip(
        tripId = startedAt.hashCode(),
        startedAt = startedAt,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = distanceKm,
        tripCategory = TripCategory.BUSINESS,
        notes = "",
        vehicleLabel = vehicleLabel,
    )
}
