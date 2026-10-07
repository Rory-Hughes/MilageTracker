package com.example.mileagetracker.navigation

import com.example.mileagetracker.domain.model.ExceptionType
import com.example.mileagetracker.domain.model.MissingField
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.model.TripSummary
import com.example.mileagetracker.ui.viewmodel.ReportsUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsGraphDestinationsTest {

    @Test
    fun `exceptions screen data excludes incomplete exceptions and scopes flagged trips`() {
        val reportsState = ReportsUiState.Success(
            summaries = emptyList<TripSummary>(),
            readinessList = listOf(
                ReportReadiness(
                    tripId = 1,
                    missingFields = listOf(MissingField.CATEGORY),
                    readinessScore = 0.75f,
                ),
            ),
            reportTripIds = setOf(1, 2),
        )
        val exceptions = listOf(
            TripException(
                tripId = 1,
                exceptionType = ExceptionType.INCOMPLETE_DATA,
                detectedAt = 10L,
            ),
            TripException(
                tripId = 2,
                exceptionType = ExceptionType.NEEDS_REVIEW,
                detectedAt = 11L,
            ),
            TripException(
                tripId = 3,
                exceptionType = ExceptionType.DUPLICATE_FLAGGED,
                detectedAt = 12L,
            ),
        )

        val result = buildExceptionsListScreenData(reportsState, exceptions)

        assertEquals(listOf(1), result.incompleteTrips.map { it.tripId })
        assertEquals(listOf(2), result.exceptions.map { it.tripId })
        assertEquals(listOf(ExceptionType.NEEDS_REVIEW), result.exceptions.map { it.exceptionType })
    }

    @Test
    fun `non success reports state yields empty exceptions screen data`() {
        val result = buildExceptionsListScreenData(
            reportsUiState = ReportsUiState.Loading,
            exceptions = listOf(
                TripException(
                    tripId = 9,
                    exceptionType = ExceptionType.NEEDS_REVIEW,
                    detectedAt = 10L,
                ),
            ),
        )

        assertEquals(emptyList<ReportReadiness>(), result.incompleteTrips)
        assertEquals(emptyList<TripException>(), result.exceptions)
    }
}
