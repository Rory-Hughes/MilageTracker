package com.example.mileagetracker.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Centralised date/time formatting.
 *
 * UI helpers default to [Locale.getDefault]; export helpers always
 * use [Locale.CANADA] because CRA reports require Canadian formatting
 * regardless of the device locale.
 */
object DateFormats {

    private val UI_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val UI_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    private val UI_TIME = DateTimeFormatter.ofPattern("h:mm a")
    private val UI_SHORT_DATE = DateTimeFormatter.ofPattern("MMM dd")
    private val UI_MONTH_DAY = DateTimeFormatter.ofPattern("MMM d")
    private val UI_MONTH_FULL_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    private val UI_DATE_WITH_DOW = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val UI_MONTH_DAY_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    private val UI_DATE_TIME_DOT = DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm")

    private val EXPORT_DATE_ISO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.CANADA)
    private val EXPORT_DATE_TIME_ISO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CANADA)
    private val EXPORT_FILE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withLocale(Locale.CANADA)
    private val EXPORT_FILE_TIMESTAMP_MS =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withLocale(Locale.CANADA)
    private val EXPORT_PDF_GENERATED =
        DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a").withLocale(Locale.CANADA)
    private val EXPORT_PDF_TRIP_DATE =
        DateTimeFormatter.ofPattern("MMM dd, yyyy").withLocale(Locale.CANADA)

    private fun zdt(epochMs: Long): ZonedDateTime =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

    private fun now(): ZonedDateTime = ZonedDateTime.now()

    fun uiDate(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_DATE.withLocale(locale).format(zdt(epochMs))

    fun uiDateTime(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_DATE_TIME.withLocale(locale).format(zdt(epochMs))

    fun uiTime(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_TIME.withLocale(locale).format(zdt(epochMs))

    fun uiShortDate(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_SHORT_DATE.withLocale(locale).format(zdt(epochMs))

    fun uiMonthDay(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_MONTH_DAY.withLocale(locale).format(zdt(epochMs))

    fun uiMonthFullDate(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_MONTH_FULL_DATE.withLocale(locale).format(zdt(epochMs))

    fun uiDateWithDayOfWeek(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_DATE_WITH_DOW.withLocale(locale).format(zdt(epochMs))

    fun uiMonthDayTime(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_MONTH_DAY_TIME.withLocale(locale).format(zdt(epochMs))

    fun uiDateTimeDot(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        UI_DATE_TIME_DOT.withLocale(locale).format(zdt(epochMs))

    fun exportDateIso(epochMs: Long): String = EXPORT_DATE_ISO.format(zdt(epochMs))

    fun exportDateTimeIsoNow(): String = EXPORT_DATE_TIME_ISO.format(now())

    fun exportFileTimestampNow(): String = EXPORT_FILE_TIMESTAMP.format(now())

    fun exportFileTimestampMillisNow(): String = EXPORT_FILE_TIMESTAMP_MS.format(now())

    fun exportPdfGeneratedNow(): String = EXPORT_PDF_GENERATED.format(now())

    fun exportPdfTripDate(epochMs: Long): String = EXPORT_PDF_TRIP_DATE.format(zdt(epochMs))
}
