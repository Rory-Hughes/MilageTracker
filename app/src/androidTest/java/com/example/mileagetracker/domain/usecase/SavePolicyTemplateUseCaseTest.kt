package com.example.mileagetracker.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SavePolicyTemplateUseCase] against a real in-memory Room DB.
 *
 * These verify the use case's own responsibilities: validation and UUID generation.
 * Repository mapping and upsert behavior are already covered separately.
 */
@RunWith(AndroidJUnit4::class)
class SavePolicyTemplateUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: PolicyRepository
    private lateinit var useCase: SavePolicyTemplateUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = PolicyRepository(db.policyTemplateDao())
        useCase = SavePolicyTemplateUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun invoke_blankId_generatesPersistedUuid() = runTest {
        useCase(
            PolicyTemplate(
                id = "",
                name = "Standard Policy",
                rateMode = RateMode.CRA_STANDARD,
            ),
        )

        val policies = repository.observePolicies().first()
        assertEquals(1, policies.size)
        val saved = policies.single()
        assertTrue(saved.id.isNotBlank())
        assertEquals("Standard Policy", saved.name)
        assertNotNull(repository.getById(saved.id))
    }

    @Test
    fun invoke_customRateMode_withPositiveRate_persistsPolicy() = runTest {
        useCase(
            PolicyTemplate(
                id = "custom-1",
                name = "Custom Team Policy",
                rateMode = RateMode.CUSTOM,
                customRateCentsPerKm = 88,
                maxDailyKm = 250,
            ),
        )

        val saved = repository.getById("custom-1")!!
        assertEquals(RateMode.CUSTOM, saved.rateMode)
        assertEquals(88, saved.customRateCentsPerKm)
        assertEquals(250, saved.maxDailyKm)
    }

    @Test
    fun invoke_blankName_throwsAndDoesNotInsertRow() = runTest {
        try {
            useCase(
                PolicyTemplate(
                    id = "",
                    name = "   ",
                    rateMode = RateMode.CRA_STANDARD,
                ),
            )
            fail("Expected IllegalArgumentException for blank name")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertTrue(repository.observePolicies().first().isEmpty())
    }

    @Test
    fun invoke_customModeWithoutRate_throwsAndDoesNotInsertRow() = runTest {
        try {
            useCase(
                PolicyTemplate(
                    id = "",
                    name = "Broken Custom Policy",
                    rateMode = RateMode.CUSTOM,
                    customRateCentsPerKm = null,
                ),
            )
            fail("Expected IllegalArgumentException for missing custom rate")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertTrue(repository.observePolicies().first().isEmpty())
    }
}
