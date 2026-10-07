package com.example.mileagetracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.mileagetracker.domain.model.TripSummary
import com.example.mileagetracker.ui.common.CurrencyText
import com.example.mileagetracker.ui.common.DistanceText
import com.example.mileagetracker.ui.theme.Spacing

/**
 * Reusable card summarising one [TripSummary] period.
 *
 * ## Layout
 * ```
 * ┌─────────────────────────────────────────────┐
 * │  May 2026                    12 trips        │
 * │─────────────────────────────────────────────│
 * │  Total km        342.5 km                   │
 * │  Business km     210.0 km    ~ $136.50       │
 * │  Personal km     132.5 km                   │
 * └─────────────────────────────────────────────┘
 * ```
 *
 * The reimbursement amount is labelled as an *estimate* ("~" prefix) because
 * the CRA rate may not yet be confirmed for the current tax year.
 *
 * Distance values are rendered via [DistanceText] and currency amounts via
 * [CurrencyText], both of which pin formatting to [CanadianUnits.CURRENCY_LOCALE]
 * regardless of device locale.
 *
 * @param summary  [TripSummary] to display.
 * @param modifier [Modifier] forwarded to the root [ElevatedCard].
 */
@Composable
fun ReportSummaryCard(
    summary:  TripSummary,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg)) {

            // Header row: period label + trip count
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = summary.periodLabel,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "${summary.tripCount} trip${if (summary.tripCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.sm))

            // Total km — Fix 3: use DistanceText composable
            KmRow(
                label = "Total",
                km    = summary.totalKm.toDouble(),
            )

            Spacer(Modifier.height(Spacing.xs))

            // Business km + reimbursement estimate
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                KmRow(
                    label    = "Business",
                    km       = summary.businessKm.toDouble(),
                    modifier = Modifier.weight(1f),
                )
                // Fix 3: use CurrencyText composable (always Locale.CANADA)
                if (summary.estimatedReimbursement > 0f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text  = "~ ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CurrencyText(
                            amount = summary.estimatedReimbursement.toDouble(),
                            style  = MaterialTheme.typography.labelMedium,
                            color  = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xs))

            // Personal km
            KmRow(
                label = "Personal",
                km    = summary.personalKm.toDouble(),
            )
        }
    }
}

@Composable
private fun KmRow(
    label:    String,
    km:       Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = "$label km",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Fix 3: use DistanceText composable instead of manual roundToInt string
        DistanceText(
            km    = km,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
