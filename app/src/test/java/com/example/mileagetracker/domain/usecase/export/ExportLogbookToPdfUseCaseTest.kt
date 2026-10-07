package com.example.mileagetracker.domain.usecase.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.model.VehicleAnnualSummary
import com.example.mileagetracker.domain.usecase.rate.GetAnnualVehicleSummaryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExportLogbookToPdfUseCaseTest {

    private lateinit var buildLogbookExportData: BuildLogbookExportDataUseCase
    private lateinit var annualSummary: GetAnnualVehicleSummaryUseCase
    private lateinit var context: Context
    private lateinit var useCase: ExportLogbookToPdfUseCase

    @Before
    fun setUp() {
        buildLogbookExportData = mockk()
        annualSummary = mockk()
        context = ApplicationProvider.getApplicationContext()
        useCase = ExportLogbookToPdfUseCase(
            buildLogbookExportData = buildLogbookExportData,
            annualSummary = annualSummary,
            context = context,
        )
        exportDir().deleteRecursively()
    }

    @Test
    fun `invoke generates all vehicle pdf and requests annual summary per assigned section`() = runTest {
        val request = LogbookExportRequest(
            vehicleId = null,
            taxYear = 2026,
            format = ExportFormat.PDF,
            categories = setOf(TripCategory.BUSINESS),
        )
        coEvery { buildLogbookExportData(request) } returns preparedExport(
            categories = listOf(TripCategory.BUSINESS),
            scopeToken = "all_vehicles",
            sections = listOf(
                section(vehicleId = 1, label = "Sedan", vehicle = vehicle(1, "Sedan")),
                section(vehicleId = 2, label = "Truck", vehicle = vehicle(2, "Truck")),
                section(vehicleId = null, label = "Unassigned", vehicle = null),
            ),
        )
        coEvery { annualSummary(1, 2026) } returns annualSummary(vehicleId = 1)
        coEvery { annualSummary(2, 2026) } returns annualSummary(vehicleId = 2)

        useCase(request)

        coVerify(exactly = 1) { annualSummary(1, 2026) }
        coVerify(exactly = 1) { annualSummary(2, 2026) }
    }

    @Test
    fun `invoke skips annual summary lookups for mixed category exports`() = runTest {
        val request = LogbookExportRequest(
            vehicleId = null,
            taxYear = 2026,
            format = ExportFormat.PDF,
            categories = setOf(TripCategory.BUSINESS, TripCategory.MEDICAL),
        )
        coEvery { buildLogbookExportData(request) } returns preparedExport(
            categories = listOf(TripCategory.BUSINESS, TripCategory.MEDICAL),
            scopeToken = "all_vehicles",
            sections = listOf(section(vehicleId = 1, label = "Sedan", vehicle = vehicle(1, "Sedan"))),
        )

        useCase(request)

        coVerify(exactly = 0) { annualSummary(any(), any()) }
    }

    @Test
    fun `invoke returns failure when export data builder throws`() = runTest {
        val request = LogbookExportRequest(
            vehicleId = 7,
            taxYear = 2026,
            format = ExportFormat.PDF,
        )
        coEvery { buildLogbookExportData(request) } throws IllegalArgumentException("Vehicle 7 not found.")

        val result = useCase(request)

        assertEquals(ExportResult.Failure("Vehicle 7 not found."), result)
    }

    private fun preparedExport(
        categories: List<TripCategory>,
        scopeToken: String,
        sections: List<PreparedLogbookSection>,
    ) = PreparedLogbookExport(
        vehicleId = if (scopeToken == "all_vehicles") null else 1,
        taxYear = 2026,
        categories = categories,
        includeRouteDetails = true,
        scopeLabel = if (scopeToken == "all_vehicles") "All vehicles" else "Sedan",
        scopeToken = scopeToken,
        sections = sections,
        totals = PreparedLogbookTotals(
            totalTripCount = sections.sumOf { it.totals.totalTripCount },
            exportedTripCount = sections.sumOf { it.totals.exportedTripCount },
            totalKm = sections.sumOf { it.totals.totalKm },
            selectedKm = sections.sumOf { it.totals.selectedKm },
            selectedUsePercent = 100.0,
        ),
    )

    private fun section(
        vehicleId: Int?,
        label: String,
        vehicle: VehicleEntity?,
    ) = PreparedLogbookSection(
        vehicleId = vehicleId,
        vehicle = vehicle,
        label = label,
        trips = listOf(
            PreparedLogbookTrip(
                tripId = (vehicleId ?: 0) + 1,
                startedAt = 1_700_000_000_000L + (vehicleId ?: 0),
                startLocation = "Home",
                endLocation = "$label Stop",
                distanceKm = 10.0,
                tripCategory = TripCategory.BUSINESS,
                notes = "",
                vehicleLabel = label,
            )
        ),
        totals = PreparedLogbookTotals(
            totalTripCount = 1,
            exportedTripCount = 1,
            totalKm = 10.0,
            selectedKm = 10.0,
            selectedUsePercent = 100.0,
        ),
    )

    private fun vehicle(id: Int, name: String) = VehicleEntity(
        id = id,
        ownerUid = "uid-1",
        displayName = name,
        fiscalYear = 2026,
    )

    private fun annualSummary(vehicleId: Int) = VehicleAnnualSummary(
        vehicleId = vehicleId,
        taxYear = 2026,
        odometerOpenKm = null,
        odometerCloseKm = null,
        odometerTotalKm = null,
        businessKm = 10.0,
        personalKm = 0.0,
        totalLoggedKm = 10.0,
        businessPercent = 100.0,
        reimbursementDollars = 7.0,
        craRateUsed = null,
        tripCount = 1,
        businessTripCount = 1,
    )

    private fun exportDir() = File(context.cacheDir, "exports")
}
