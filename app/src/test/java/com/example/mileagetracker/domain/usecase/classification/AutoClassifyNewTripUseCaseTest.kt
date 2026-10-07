package com.example.mileagetracker.domain.usecase.classification

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutoClassifyNewTripUseCaseTest {

    private lateinit var autoClassifyTripUseCase: AutoClassifyTripUseCase
    private lateinit var useCase: AutoClassifyNewTripUseCase

    @Before
    fun setUp() {
        autoClassifyTripUseCase = mockk()
        useCase = AutoClassifyNewTripUseCase(autoClassifyTripUseCase)
    }

    @Test
    fun `delegates to autoClassifyTripUseCase with force false`() = runTest {
        val expected = AutoClassifyResult.Classified(
            category = com.example.mileagetracker.data.local.entity.TripCategory.BUSINESS,
            confidence = 0.9f,
            ruleName = "Rule"
        )
        coEvery { 
            autoClassifyTripUseCase(driveId = 1, actorUid = any(), force = false) 
        } returns expected

        val result = useCase(1)

        assertEquals(expected, result)
    }

    @Test
    fun `uses SYSTEM_ACTOR by default`() = runTest {
        coEvery {
            autoClassifyTripUseCase(driveId = 7, actorUid = any(), force = false)
        } returns AutoClassifyResult.NotFound

        useCase(7)

        coVerify {
            autoClassifyTripUseCase(
                driveId = 7,
                actorUid = AutoClassifyTripUseCase.SYSTEM_ACTOR,
                force = false,
            )
        }
    }

    @Test
    fun `forwards an explicit actorUid`() = runTest {
        coEvery {
            autoClassifyTripUseCase(driveId = 3, actorUid = "manager-9", force = false)
        } returns AutoClassifyResult.NotFound

        useCase(3, actorUid = "manager-9")

        coVerify {
            autoClassifyTripUseCase(driveId = 3, actorUid = "manager-9", force = false)
        }
    }

    @Test
    fun `passes through a non-Classified result unchanged`() = runTest {
        coEvery {
            autoClassifyTripUseCase(driveId = 1, actorUid = any(), force = false)
        } returns AutoClassifyResult.NotFound

        val result = useCase(1)

        assertEquals(AutoClassifyResult.NotFound, result)
    }
}
