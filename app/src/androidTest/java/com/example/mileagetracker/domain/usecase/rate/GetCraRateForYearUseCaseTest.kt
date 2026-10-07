package com.example.mileagetracker.domain.usecase.rate

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [GetCraRateForYearUseCase] against a real (in-memory)
 * Room DB.
 *
 * ## Behaviour under test
 * - **No rate seeded**: both [invoke] (Flow) and [once] return `null`.
 * - **Rate seeded**: both access patterns return the correct [CraRateEntity].
 * - **Multiple rows for same year**: the row with the latest [effectiveFrom]
 *   is returned (mid-year revision handling).
 * - **Rate isolation**: seeding year A does not affect queries for year B.
 */
@RunWith(AndroidJUnit4::class)
class GetCraRateForYearUseCaseTest {

    private lateinit var db:      MileageDatabase
    private lateinit var useCase: GetCraRateForYearUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        val rateRepo = RateRepositoryImpl(db.craRateDao())
        useCase      = GetCraRateForYearUseCase(rateRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private fun rate(
        taxYear:             Int  = 2024,
        firstTierCents:      Int  = 70,
        additionalCents:     Int  = 64,
        firstTierLimitKm:    Int  = 5_000,
        effectiveFrom:       Long = jan1Millis(taxYear),
    ) = CraRateEntity(
        taxYear               = taxYear,
        firstTierRateCents    = firstTierCents,
        additionalKmRateCents = additionalCents,
        firstTierLimitKm      = firstTierLimitKm,
        sourceLabel           = "Test",
        effectiveFrom         = effectiveFrom,
    )

    private fun jan1Millis(year: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private suspend fun seed(vararg rates: CraRateEntity) {
        val repo = RateRepositoryImpl(db.craRateDao())
        rates.forEach { repo.insertRate(it) }
    }

    // ── No rate seeded ────────────────────────────────────────────────────────────

    @Test
    fun invoke_flow_noRateSeeded_emitsNull() = runTest {
        val emitted = useCase(2024).first()
        assertNull(emitted)
    }

    @Test
    fun once_noRateSeeded_returnsNull() = runTest {
        val result = useCase.once(2024)
        assertNull(result)
    }

    // ── Single rate seeded ────────────────────────────────────────────────────────

    @Test
    fun invoke_flow_rateSeeded_emitsCorrectEntity() = runTest {
        seed(rate(taxYear = 2024, firstTierCents = 70))

        val emitted = useCase(2024).first()

        assertNotNull(emitted)
        assertEquals(2024, emitted!!.taxYear)
        assertEquals(70,   emitted.firstTierRateCents)
    }

    @Test
    fun once_rateSeeded_returnsCorrectEntity() = runTest {
        seed(rate(taxYear = 2024, firstTierCents = 70, additionalCents = 64))

        val result = useCase.once(2024)

        assertNotNull(result)
        assertEquals(70, result!!.firstTierRateCents)
        assertEquals(64, result.additionalKmRateCents)
    }

    @Test
    fun once_correctFirstTierLimitKm_returned() = runTest {
        seed(rate(taxYear = 2024, firstTierLimitKm = 5_000))

        val result = useCase.once(2024)

        assertEquals(5_000, result!!.firstTierLimitKm)
    }

    // ── Mid-year revision: latest effectiveFrom wins ──────────────────────────────

    @Test
    fun once_twoRowsForSameYear_latestEffectiveFromReturned() = runTest {
        val earlyRate = rate(taxYear = 2024, firstTierCents = 68, effectiveFrom = jan1Millis(2024))
        val lateRate  = rate(taxYear = 2024, firstTierCents = 70, effectiveFrom = jan1Millis(2024) + 86_400_000L)
        seed(earlyRate, lateRate)

        val result = useCase.once(2024)

        assertNotNull(result)
        assertEquals("Latest effectiveFrom should win", 70, result!!.firstTierRateCents)
    }

    // ── Year isolation ────────────────────────────────────────────────────────────

    @Test
    fun once_queryForDifferentYear_returnsNull() = runTest {
        seed(rate(taxYear = 2024))

        val result = useCase.once(2025)

        assertNull(result)
    }

    @Test
    fun once_multipleYearsSeeded_correctYearReturned() = runTest {
        seed(
            rate(taxYear = 2023, firstTierCents = 68),
            rate(taxYear = 2024, firstTierCents = 70),
            rate(taxYear = 2025, firstTierCents = 73),
        )

        val result2024 = useCase.once(2024)
        val result2025 = useCase.once(2025)

        assertEquals(70, result2024!!.firstTierRateCents)
        assertEquals(73, result2025!!.firstTierRateCents)
    }

    // ── Flow reactive behaviour ───────────────────────────────────────────────────

    @Test
    fun invoke_flow_andOnce_returnSameData() = runTest {
        seed(rate(taxYear = 2026, firstTierCents = 73))

        val fromFlow = useCase(2026).first()
        val fromOnce = useCase.once(2026)

        assertNotNull(fromFlow)
        assertNotNull(fromOnce)
        assertEquals(fromFlow!!.taxYear,            fromOnce!!.taxYear)
        assertEquals(fromFlow.firstTierRateCents,   fromOnce.firstTierRateCents)
        assertEquals(fromFlow.additionalKmRateCents, fromOnce.additionalKmRateCents)
    }
}
