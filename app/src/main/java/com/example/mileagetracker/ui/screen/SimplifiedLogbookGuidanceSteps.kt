package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus
import com.example.mileagetracker.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Step1Eligibility(
    status: SimplifiedLogbookStatus,
    onSelectBaseYear: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeading(step = 1, title = "Eligibility Check")

        if (status.isEligible) {
            EligibleBanner()
            EligibilitySummaryCard(status)
            BaseYearPicker(status, onSelectBaseYear)
        } else {
            IneligibleBanner()
            IneligibleGuidanceCard(status)
        }
    }
}

@Composable
private fun EligibilitySummaryCard(status: SimplifiedLogbookStatus) {
    InfoCard {
        StatRow(label = "Base Year", value = status.baseYear.toString())
        StatRow(label = "Trips Recorded", value = "${status.baseYearTripCount} trips")
        StatRow(
            label = "Business Use",
            value = "${status.baseYearBusinessUsePercent?.roundToInt() ?: "-"}%",
            bold = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaseYearPicker(
    status: SimplifiedLogbookStatus,
    onSelectBaseYear: (Int) -> Unit,
) {
    if (status.availableBaseYears.size <= 1) {
        return
    }

    var expanded by remember { mutableStateOf(false) }
    Text(
        text = "Multiple qualifying years found - select which to use as your base:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = status.baseYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Base Year") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            status.availableBaseYears.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onSelectBaseYear(year)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun IneligibleGuidanceCard(status: SimplifiedLogbookStatus) {
    InfoCard {
        Text(
            text = status.guidanceText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.simplified_logbook_requirement_full_year),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Step2SamplePeriod(status: SimplifiedLogbookStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeading(step = 2, title = "Sample Period")

        Text(
            text = stringResource(R.string.simplified_logbook_requirement_sample),
            style = MaterialTheme.typography.bodyMedium,
        )

        SampleRangeCard(status)
        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = "Your current sample progress:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )

        SampleProgressBar(status)
        SampleProgressSummary(status)
        SampleCompletionStatus(status)
    }
}

@Composable
private fun SampleRangeCard(status: SimplifiedLogbookStatus) {
    InfoCard {
        StatRow(
            label = "Base-Year Business Use",
            value = "${status.baseYearBusinessUsePercent?.roundToInt() ?: "-"}%",
        )
        StatRow(
            label = "Allowed Sample Range",
            value = allowedSampleRange(status),
        )
    }
}

private fun allowedSampleRange(status: SimplifiedLogbookStatus): String {
    val base = status.baseYearBusinessUsePercent ?: 0.0
    val low = (base - SimplifiedLogbookStatus.MAX_RATIO_DEVIATION_PERCENT)
        .coerceAtLeast(0.0)
        .roundToInt()
    val high = (base + SimplifiedLogbookStatus.MAX_RATIO_DEVIATION_PERCENT)
        .coerceAtMost(100.0)
        .roundToInt()
    return "$low% - $high%"
}

@Composable
private fun SampleProgressBar(status: SimplifiedLogbookStatus) {
    LinearProgressIndicator(
        progress = { (status.sampleProgressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    )
}

@Composable
private fun SampleProgressSummary(status: SimplifiedLogbookStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${status.currentSampleKm.roundToInt()} km recorded",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "${status.sampleProgressPercent.roundToInt()}% complete",
            style = MaterialTheme.typography.bodySmall,
            color = if (status.sampleProgressPercent >= 100.0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SampleCompletionStatus(status: SimplifiedLogbookStatus) {
    if (status.sampleProgressPercent < 100.0) {
        Text(
            text = "Target: ~${status.requiredSampleKm.roundToInt()} business km " +
                "(about 25% of your base-year business km).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "Sample period complete for this year.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
