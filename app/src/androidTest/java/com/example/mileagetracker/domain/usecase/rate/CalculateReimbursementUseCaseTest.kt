package com.example.mileagetracker.domain.usecase.rate

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.CraRateEntity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the pure [CalculateReimbursementUseCase.calculate]
 * overload.
 *
 * [calculate] accepts an already-resolved [CraRateEntity] and float distances,
 * performs the CRA two-tier math entirely in-process, and returns a
 * [ReimbursementResult].  No database or DataStore is involved — these tests
 * exercise the arithmetic and rounding logic directly.
 *
 * ## CRA two-tier formula (from the class KDoc)
 * ```
 * firstTierKm  = min(ytdBefore + newKm, limit) − min(ytdBefore, limit)
 * secondTierKm = newKm − firstTierKm
 * amountCents  = firstTierKm × firstTierRateCents
 *              + secondTierKm × additionalKmRateCents
 * ```
 *
 * ## Rounding
 * Distances are rounded **half-up** to the nearest whole km before
 * multiplication: `(distance + 0.5f).toInt()`.
 * - 0.4 km → 0 km (0.4 + 0.5 = 0.9 → 0)
 * - 0.5 km → 1 km (0.5 + 0.5 = 1.0 → 1)
 *
 * The [CalculateReimbursementUseCase] constructor requires repositories only
 * for its `suspend operator invoke()` path; the pure [calculate] method does
 * not call them, so relaxed MockK stubs are used as placeholders.
 */
@RunWith(AndroidJUnit4::class)
class CalculateReimbursementUseCaseTest {

    private lateinit var useCase: CalculateReimbursementUseCase

    @Before
    fun setUp() {
        useCase = CalculateReimbursementUseCase(
            rateRepository     = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
        )
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    /** 2025 CRA rate: 70¢/km first tier, 64¢/km additional, 5 000 km threshold. */
    private val rate2025 = CraRateEntity(
        taxYear               = 2025,
        firstTierRateCents    = 70,
        firstTierLimitKm      = 5_000,
        additionalKmRateCents = 64,
        sourceLabel           = "CRA Test 2025",
        effectiveFrom         = 1L,
    )

    // ── All first-tier kilometres ──────────────────────────────────────────────

    @Test
    fun calculate_allFirstTier_ytdZero_correctAmountAndFlags() {
        // ytdBefore=0, newKm=100 → all 100 km within the 5 000 km first tier.
        val result = useCase.calculate(rate2025, newDistanceKm = 100f, ytdKmBefore = 0f)

        assertEquals(100, result.firstTierKm)
        assertEquals(0,   result.secondTierKm)
        assertEquals(100 * 70, result.amountCents) // 7 000¢ = $70.00
        assertEquals(70.0, result.amountDollars, 0.001)
        assertTrue(result.isAllFirstTier)
        assertFalse(result.isAllSecondTier)
    }

    @Test
    fun calculate_allFirstTier_withExistingYtd_staysWithinTier() {
        // ytdBefore=2 000, newKm=500 → totalYtd=2 500 still < 5 000.
        val result = useCase.calculate(rate2025, newDistanceKm = 500f, ytdKmBefore = 2_000f)

        assertEquals(500, result.firstTierKm)
        assertEquals(0,   result.secondTierKm)
        assertEquals(500 * 70, result.amountCents)
        assertTrue(result.isAllFirstTier)
    }

    // ── All second-tier kilometres ─────────────────────────────────────────────

    @Test
    fun calculate_allSecondTier_ytdAlreadyPastLimit() {
        // ytdBefore=5 000 (already at limit), newKm=1 000 → all second-tier.
        val result = useCase.calculate(rate2025, newDistanceKm = 1_000f, ytdKmBefore = 5_000f)

        assertEquals(0,     result.firstTierKm)
        assertEquals(1_000, result.secondTierKm)
        assertEquals(1_000 * 64, result.amountCents) // 64 000¢ = $640.00
        assertEquals(640.0, result.amountDollars, 0.001)
        assertFalse(result.isAllFirstTier)
        assertTrue(result.isAllSecondTier)
    }

    @Test
    fun calculate_allSecondTier_ytdWellAboveLimit() {
        val result = useCase.calculate(rate2025, newDistanceKm = 200f, ytdKmBefore = 8_000f)

        assertEquals(0,   result.firstTierKm)
        assertEquals(200, result.secondTierKm)
        assertEquals(200 * 64, result.amountCents)
        assertTrue(result.isAllSecondTier)
    }

    // ── Split across tiers ────────────────────────────────────────────────────

    @Test
    fun calculate_splitAcrossTiers_correctBreakdown() {
        // ytdBefore=4 800, newKm=500
        // totalYtd = 5 300
        // firstTierKm  = min(5300,5000) − min(4800,5000) = 5000 − 4800 = 200
        // secondTierKm = 500 − 200 = 300
        val result = useCase.calculate(rate2025, newDistanceKm = 500f, ytdKmBefore = 4_800f)

        assertEquals(200, result.firstTierKm)
        assertEquals(300, result.secondTierKm)
        val expected = 200 * 70 + 300 * 64 // 14 000 + 19 200 = 33 200
        assertEquals(expected, result.amountCents)
        assertEquals(332.0, result.amountDollars, 0.001)
        assertFalse(result.isAllFirstTier)
        assertFalse(result.isAllSecondTier)
    }

    @Test
    fun calculate_exactTierBoundary_1kmCrossesLimit() {
        // ytdBefore=4 999, newKm=1 → totalYtd=5 000, entirely first-tier.
        val result = useCase.calculate(rate2025, newDistanceKm = 1f, ytdKmBefore = 4_999f)

        assertEquals(1, result.firstTierKm)
        assertEquals(0, result.secondTierKm)
        assertEquals(70, result.amountCents)
        assertTrue(result.isAllFirstTier)
    }

    @Test
    fun calculate_twoKmStraddlingLimit() {
        // ytdBefore=4 999, newKm=2 → totalYtd=5 001
        // firstTierKm  = min(5001,5000) − min(4999,5000) = 5000 − 4999 = 1
        // secondTierKm = 2 − 1 = 1
        val result = useCase.calculate(rate2025, newDistanceKm = 2f, ytdKmBefore = 4_999f)

        assertEquals(1, result.firstTierKm)
        assertEquals(1, result.secondTierKm)
        assertEquals(1 * 70 + 1 * 64, result.amountCents) // 134¢
    }

    // ── Zero distance ─────────────────────────────────────────────────────────

    @Test
    fun calculate_zeroDistance_amountIsZero() {
        val result = useCase.calculate(rate2025, newDistanceKm = 0f, ytdKmBefore = 0f)

        assertEquals(0, result.firstTierKm)
        assertEquals(0, result.secondTierKm)
        assertEquals(0, result.amountCents)
        assertEquals(0.0, result.amountDollars, 0.001)
    }

    // ── Float rounding (half-up) ──────────────────────────────────────────────

    @Test
    fun calculate_pointFourKm_roundsDownToZero() {
        // (0.4 + 0.5).toInt() = 0 → no reimbursable km.
        val result = useCase.calculate(rate2025, newDistanceKm = 0.4f, ytdKmBefore = 0f)

        assertEquals(0, result.firstTierKm + result.secondTierKm)
        assertEquals(0, result.amountCents)
    }

    @Test
    fun calculate_pointFiveKm_roundsUpToOne() {
        // (0.5 + 0.5).toInt() = 1 → 1 first-tier km.
        val result = useCase.calculate(rate2025, newDistanceKm = 0.5f, ytdKmBefore = 0f)

        assertEquals(1, result.firstTierKm)
        assertEquals(0, result.secondTierKm)
        assertEquals(70, result.amountCents)
    }

    @Test
    fun calculate_ytdBeforeRounding_fractionalYtdRoundsBeforeApplying() {
        // ytdBefore=0.6f → rounds to 1 whole km.
        // newKm=1f → 1 whole km.
        // totalYtd = 1 + 1 = 2 → firstTierKm = min(2,5000) - min(1,5000) = 1
        val result = useCase.calculate(rate2025, newDistanceKm = 1f, ytdKmBefore = 0.6f)

        assertEquals(1, result.firstTierKm)
        assertEquals(0, result.secondTierKm)
    }

    // ── Result field propagation ───────────────────────────────────────────────

    @Test
    fun calculate_resultFieldsReflectInputs() {
        val result = useCase.calculate(rate2025, newDistanceKm = 100f, ytdKmBefore = 50f)

        assertEquals(2025, result.taxYear)
        assertEquals(100f, result.newDistanceKm)
        assertEquals(50f,  result.ytdKmBefore)
        assertEquals(5_000, result.firstTierLimitKm)
        assertEquals(70, result.firstTierRateCents)
        assertEquals(64, result.additionalRateCents)
    }

    // ── Different rate entity ─────────────────────────────────────────────────

    @Test
    fun calculate_differentRate_usesProvidedRateValues() {
        // Hypothetical future rate: 80¢ first tier, 75¢ additional, 6 000 km limit.
        val futureRate = CraRateEntity(
            taxYear               = 2030,
            firstTierRateCents    = 80,
            firstTierLimitKm      = 6_000,
            additionalKmRateCents = 75,
            sourceLabel           = "Hypothetical",
            effectiveFrom         = 1L,
        )

        val result = useCase.calculate(futureRate, newDistanceKm = 1_000f, ytdKmBefore = 0f)

        assertEquals(1_000, result.firstTierKm)
        assertEquals(0,     result.secondTierKm)
        assertEquals(1_000 * 80, result.amountCents)
        assertEquals(800.0, result.amountDollars, 0.001)
    }
}
