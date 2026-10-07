package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DomainModelTests {

    @Test
    fun `AppLanguage toLocale returns correct locale`() {
        assertNull(AppLanguage.SYSTEM_DEFAULT.toLocale())
        assertEquals(Locale.ENGLISH, AppLanguage.ENGLISH.toLocale())
        assertEquals(Locale.CANADA_FRENCH, AppLanguage.FRENCH.toLocale())
    }

    @Test
    fun `AppLanguage fromTag matches tags correctly`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr-CA"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.SYSTEM_DEFAULT, AppLanguage.fromTag("invalid"))
        assertEquals(AppLanguage.SYSTEM_DEFAULT, AppLanguage.fromTag(null))
    }

    @Test
    fun `TripCategory selectableEntries excludes unclassified`() {
        assertFalse(TripCategory.selectableEntries.contains(TripCategory.UNCLASSIFIED))
        assertTrue(TripCategory.selectableEntries.contains(TripCategory.BUSINESS))
    }

    @Test
    fun `ExpenseCategory fromKey handles fixed and other`() {
        assertEquals(ExpenseCategory.Fuel, ExpenseCategory.fromKey("Fuel"))
        assertEquals(ExpenseCategory.Other("Car wash"), ExpenseCategory.fromKey("Other:Car wash"))
        assertEquals(ExpenseCategory.Other("Random"), ExpenseCategory.fromKey("Random"))
    }

    @Test
    fun `VehicleAnnualSummary display helpers work`() {
        val summary = VehicleAnnualSummary(
            vehicleId = 1,
            taxYear = 2025,
            odometerOpenKm = 10000,
            odometerCloseKm = 12000,
            odometerTotalKm = 2000,
            businessKm = 1500.0,
            personalKm = 500.0,
            totalLoggedKm = 2000.0,
            businessPercent = 75.0,
            reimbursementDollars = 1050.0,
            craRateUsed = null,
            tripCount = 10,
            businessTripCount = 8,
        )

        Locale.setDefault(Locale.US)

        assertEquals("10,000 km \u2192 12,000 km (2,000 km)", summary.odometerDisplayLine())
        assertEquals("75.0 %", summary.businessPercentDisplay())
        assertEquals("$1,050.00", summary.reimbursementDisplay())
    }

    @Test
    fun `CraRate mapping and display work`() {
        val entity = CraRateEntity(
            taxYear = 2025,
            firstTierRateCents = 70,
            firstTierLimitKm = 5000,
            additionalKmRateCents = 64,
            sourceLabel = "Seed",
            effectiveFrom = 0L,
        )
        val domain = entity.toDomain()

        assertEquals(2025, domain.year)
        assertEquals(0.70, domain.tier1RateDollars, 0.001)
        assertEquals("$0.70 / km (first 5,000 km), $0.64 / km thereafter", domain.displayLabel())
    }

    @Test
    fun `CanadianAddress provinceName handles locales`() {
        val address = CanadianAddress("S", "C", "QC", "P")
        assertEquals("Quebec", address.provinceName(Locale.ENGLISH))
        assertEquals("Qu\u00E9bec", address.provinceName(Locale.FRENCH))
    }

    @Test
    fun `ReportReadiness isReady logic`() {
        assertTrue(ReportReadiness(1, emptyList(), 1.0f).isReady)
        assertFalse(ReportReadiness(1, listOf(MissingField.PURPOSE), 0.75f).isReady)
    }

    @Test
    fun `TripException isActive logic`() {
        assertTrue(TripException(1, ExceptionType.NEEDS_REVIEW, 1000L).isActive)
        assertFalse(TripException(1, ExceptionType.NEEDS_REVIEW, 1000L, 2000L).isActive)
    }

    @Test
    fun `ExportFormat mimeType mapping`() {
        assertEquals("application/pdf", ExportFormat.PDF.mimeType)
        assertEquals("text/csv", ExportFormat.CSV.mimeType)
    }

    @Test
    fun `ReimbursementCalculation display and breakdown`() {
        val rate = CraRate(2025, 70, 64, 5000)
        val calc = ReimbursementCalculation(
            inputKm = 6000.0,
            year = 2025,
            tier1Km = 5000.0,
            tier2Km = 1000.0,
            tier1Amount = 3500.0,
            tier2Amount = 640.0,
            totalAmount = 4140.0,
            rateUsed = rate,
        )

        Locale.setDefault(Locale.US)

        assertEquals("$4,140.00 (6,000 km)", calc.totalLine())
        val lines = calc.breakdownLines()
        assertEquals(2, lines.size)
        assertEquals("5,000 km \u00D7 $0.70 = $3,500.00", lines[0])
        assertEquals("1,000 km \u00D7 $0.64 = $640.00", lines[1])
    }

    @Test
    fun `DuplicateCheckResult thresholds are correct`() {
        assertEquals(0.40f, DuplicateCheckResult.FLAGGED_THRESHOLD)
        assertEquals(0.80f, DuplicateCheckResult.PROBABLE_DUPLICATE_THRESHOLD)
    }

    @Test
    fun `CanadianUnits constants`() {
        assertEquals("km", CanadianUnits.DISTANCE_UNIT)
        assertEquals("CAD", CanadianUnits.CURRENCY_CODE)
        assertEquals(Locale.CANADA, CanadianUnits.CURRENCY_LOCALE)
    }
}
