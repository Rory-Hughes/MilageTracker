package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.usecase.GetReportReadinessUseCase
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import com.example.mileagetracker.ui.viewmodel.ReportsViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingExportButton_callsStandaloneExportNavigation() {
        val getTripSummaryUseCase: GetTripSummaryUseCase = mockk()
        val getReportReadinessUseCase: GetReportReadinessUseCase = mockk()
        val currentUserProvider: CurrentUserProvider = mockk()
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { getTripSummaryUseCase(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { getTripSummaryUseCase.getDrivesForYear(any(), any(), any()) } returns emptyList()
        every { getReportReadinessUseCase(any()) } returns emptyList<ReportReadiness>()

        val viewModel = ReportsViewModel(
            getTripSummaryUseCase = getTripSummaryUseCase,
            getReportReadinessUseCase = getReportReadinessUseCase,
            currentUserProvider = currentUserProvider,
        )

        var openedExport = false
        composeRule.setContent {
            ReportsScreen(
                activeVehicles = listOf(
                    VehicleEntity(id = 1, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2026)
                ),
                onOpenExport = { openedExport = true },
                viewModel = viewModel,
            )
        }

        composeRule.onNodeWithText("Export Logbook").performClick()
        assertTrue(openedExport)
    }
}
