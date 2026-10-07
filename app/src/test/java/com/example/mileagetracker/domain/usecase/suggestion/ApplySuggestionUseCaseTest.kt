package com.example.mileagetracker.domain.usecase.suggestion

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplySuggestionUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var classifyTripUseCase: ClassifyTripUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: ApplySuggestionUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        auditRepository = mockk(relaxed = true)
        classifyTripUseCase = mockk()
        currentUserProvider = mockk()
        useCase = ApplySuggestionUseCase(
            driveRepository,
            auditRepository,
            classifyTripUseCase,
            currentUserProvider
        )
    }

    @Test
    fun `returns false if classifyTripUseCase fails`() = runTest {
        coEvery { classifyTripUseCase(any(), any(), any(), any()) } returns false
        
        val suggestion = TripSuggestion(TripCategory.BUSINESS, "Work", null, 0.9f, emptyList())
        val result = useCase(1, suggestion, "u1")
        
        assertFalse(result)
    }

    @Test
    fun `applies suggestion and records audit event`() = runTest {
        val suggestion = TripSuggestion(
            suggestedCategory = TripCategory.BUSINESS,
            suggestedPurpose = "Client meeting",
            suggestedVehicleId = null,
            matchConfidence = 0.85f,
            basedOnTripIds = listOf(10, 11)
        )
        coEvery { classifyTripUseCase(1, TripCategory.BUSINESS, "Client meeting", "u1") } returns true

        val result = useCase(1, suggestion, "u1")

        assertTrue(result)
        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.SUGGESTION_ACCEPTED,
                newValue = match { it.contains("confidence=0.85") && it.contains("basedOn=10|11") }
            )
        }
    }
}
