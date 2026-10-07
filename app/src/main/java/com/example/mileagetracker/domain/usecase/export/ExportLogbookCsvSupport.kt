package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.formatting.CanadianAddressFormatter
import com.example.mileagetracker.util.DateFormats
import java.io.OutputStreamWriter
import java.util.Locale

internal fun writeSections(
    writer: OutputStreamWriter,
    prepared: PreparedLogbookExport,
) {
    prepared.sections.forEach { section ->
        section.trips.forEach { trip ->
            writer.writeLine(buildTripRow(prepared, trip))
        }
        writer.writeLine(buildSubtotalRow(section))
    }
}

private fun buildTripRow(
    prepared: PreparedLogbookExport,
    trip: PreparedLogbookTrip,
): String {
    val date = DateFormats.exportDateIso(trip.startedAt)
    val startLocation = if (prepared.includeRouteDetails) {
        CanadianAddressFormatter.normalizeRawLocationForExport(trip.startLocation)
    } else {
        ""
    }
    val endLocation = if (prepared.includeRouteDetails) {
        CanadianAddressFormatter.normalizeRawLocationForExport(trip.endLocation)
    } else {
        ""
    }
    val destination = CanadianAddressFormatter.normalizeRawLocationForExport(trip.endLocation)

    return listOf(
        date,
        startLocation,
        endLocation,
        destination,
        trip.tripCategory.displayName,
        String.format(Locale.CANADA, "%.2f", trip.distanceKm),
        trip.vehicleLabel,
        trip.notes,
    ).joinToString(",") { value -> value.csvEscape() }
}

private fun buildSubtotalRow(section: PreparedLogbookSection): String =
    listOf(
        "SUBTOTAL",
        "",
        "",
        "",
        "",
        String.format(Locale.CANADA, "%.2f", section.totals.selectedKm),
        section.label,
        "",
    ).joinToString(",") { value -> value.csvEscape() }

internal fun writeTotals(
    writer: OutputStreamWriter,
    prepared: PreparedLogbookExport,
) {
    writer.writeLine(
        listOf(
            "TOTALS",
            "",
            "",
            "",
            "",
            String.format(Locale.CANADA, "%.2f", prepared.totals.selectedKm),
            prepared.scopeLabel,
            buildPercentSummary(prepared),
        ).joinToString(",") { value -> value.csvEscape() },
    )
}

private fun buildPercentSummary(prepared: PreparedLogbookExport): String =
    "${prepared.percentLabel}: " +
        String.format(Locale.CANADA, "%.2f%%", prepared.totals.selectedUsePercent)

internal fun writeMetadata(
    writer: OutputStreamWriter,
    prepared: PreparedLogbookExport,
) {
    writer.writeLine("")
    writer.writeLine("# Scope: ${prepared.scopeLabel}")
    writer.writeLine("# Selected categories: ${prepared.categorySummary}")
    writer.writeLine(
        "# ${prepared.distanceLabel}: " +
            String.format(Locale.CANADA, "%.2f km", prepared.totals.selectedKm),
    )
    writer.writeLine(
        "# Total logged km: " +
            String.format(Locale.CANADA, "%.2f km", prepared.totals.totalKm),
    )
    writer.writeLine("# Generated at: ${exportedTimestamp()}")
}

private fun exportedTimestamp(): String = DateFormats.exportDateTimeIsoNow()

private fun String.csvEscape(): String =
    if (any(::requiresCsvEscaping)) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

private fun requiresCsvEscaping(character: Char): Boolean =
    character == ',' || character == '"' || character == '\n' || character == '\r'

internal fun OutputStreamWriter.writeLine(value: String) {
    write(value)
    write("\n")
}
