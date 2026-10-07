package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.VehicleAnnualSummary
import com.example.mileagetracker.ui.theme.Spacing

@Composable
internal fun SummaryContent(
    summary: VehicleAnnualSummary,
    onOpenOdometer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OdometerSummaryCard(summary = summary)
        MissingOdometerCard(
            summary = summary,
            onOpenOdometer = onOpenOdometer,
        )
        KilometerBreakdownCard(summary = summary)
        AnnualSummaryHighlightsCard(summary = summary)
        TripCountsCard(summary = summary)
        CraRateSummaryCard(summary = summary)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun OdometerSummaryCard(summary: VehicleAnnualSummary) {
    val hasMissingReading = summary.odometerOpenKm == null || summary.odometerCloseKm == null
    SummaryCard(title = stringResource(R.string.odometer_title) + " (${summary.taxYear})") {
        Text(
            text = summary.odometerDisplayLine(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasMissingReading) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun MissingOdometerCard(
    summary: VehicleAnnualSummary,
    onOpenOdometer: () -> Unit,
) {
    val hasMissingReading = summary.odometerOpenKm == null || summary.odometerCloseKm == null
    if (!hasMissingReading) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.odometer_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.odometer_cra_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Button(onClick = onOpenOdometer) {
                Text("Record")
            }
        }
    }
}

@Composable
private fun KilometerBreakdownCard(summary: VehicleAnnualSummary) {
    SummaryCard(title = stringResource(R.string.annual_summary_total_km)) {
        KmRow(label = stringResource(R.string.annual_summary_business_km), km = summary.businessKm)
        KmRow(label = stringResource(R.string.annual_summary_personal_km), km = summary.personalKm)
        KmRow(label = stringResource(R.string.annual_summary_total_km), km = summary.totalLoggedKm, bold = true)
    }
}

@Composable
private fun AnnualSummaryHighlightsCard(summary: VehicleAnnualSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.annual_summary_business_pct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = summary.businessPercentDisplay(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.annual_summary_reimbursement),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = summary.reimbursementDisplay(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TripCountsCard(summary: VehicleAnnualSummary) {
    SummaryCard(title = "Trip Counts") {
        LabelValueRow(label = "Total trips", value = summary.tripCount.toString())
        LabelValueRow(label = "Business trips", value = summary.businessTripCount.toString())
    }
}

@Composable
private fun CraRateSummaryCard(summary: VehicleAnnualSummary) {
    val rate = summary.craRateUsed ?: return

    SummaryCard(title = stringResource(R.string.annual_summary_cra_rate)) {
        LabelValueRow(
            label = "First %,d km".format(rate.tier1ThresholdKm),
            value = "${rate.tier1RateCentsPerKm}C/km",
        )
        LabelValueRow(
            label = "Over %,d km".format(rate.tier1ThresholdKm),
            value = "${rate.tier2RateCentsPerKm}C/km",
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun KmRow(
    label: String,
    km: Double,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "%.1f km".format(km),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LabelValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Unspecified,
        )
    }
}
