package com.example.mileagetracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.MissingField
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.ui.theme.MileageTrackerTheme
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke

/**
 * Card shown at the top of [ReportsScreen] when one or more trips in the
 * selected period have a [ReportReadiness.readinessScore] below 1.0.
 *
 * Shows count of incomplete trips, a progress bar of ready vs total, and
 * a "Review Trips" button that navigates to [ExceptionsListScreen].
 *
 * Returns immediately (renders nothing) when all trips are ready.
 *
 * @param readinessList  Full scored list for the current period.
 * @param onReviewTrips  Navigates to ExceptionsListScreen.
 */
@Composable
fun ReportReadinessCard(
    readinessList: List<ReportReadiness>,
    onReviewTrips: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCount = readinessList.size
    if (totalCount == 0) return

    val incompleteCount = readinessList.count { !it.isReady }
    if (incompleteCount == 0) return

    val readyCount = totalCount - incompleteCount
    val readyFraction = (totalCount - incompleteCount).toFloat() / totalCount.toFloat()

    Card(
        onClick = onReviewTrips,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.thin),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            ReportReadinessHeader(incompleteCount = incompleteCount)
            Spacer(Modifier.height(Spacing.sm))
            ReportReadinessBody()
            Spacer(Modifier.height(Spacing.md))
            ReportReadinessProgress(readyFraction = readyFraction)
            Spacer(Modifier.height(Spacing.xs))
            ReportReadinessProgressLabel(
                readyCount = readyCount,
                totalCount = totalCount,
            )
            Spacer(Modifier.height(Spacing.md))
            ReportReadinessActions(onReviewTrips = onReviewTrips)
        }
    }
}

@Composable
private fun ReportReadinessHeader(incompleteCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = pluralStringResource(
                R.plurals.report_readiness_incomplete_title,
                incompleteCount,
                incompleteCount,
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun ReportReadinessBody() {
    Text(
        text = stringResource(R.string.report_readiness_incomplete_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun ReportReadinessProgress(readyFraction: Float) {
    // TODO(design-system): once Theme.kt overrides the full ColorScheme
    //  (audit action #1 — replace the IDE-template Purple40/80 palette),
    //  re-verify that `primary` reads cleanly on `errorContainer` in both
    //  light and dark schemes. The pairing is not in M3's WCAG-validated set.
    LinearProgressIndicator(
        progress = { readyFraction },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
    )
}

@Composable
private fun ReportReadinessProgressLabel(
    readyCount: Int,
    totalCount: Int,
) {
    Text(
        text = pluralStringResource(
            R.plurals.report_readiness_progress_label,
            totalCount,
            readyCount,
            totalCount,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun ReportReadinessActions(onReviewTrips: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = onReviewTrips,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.report_readiness_review_button))
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "Incomplete — many", showBackground = true)
@Composable
private fun ReportReadinessCardManyIncompletePreview() {
    MileageTrackerTheme {
        ReportReadinessCard(
            readinessList = listOf(
                ReportReadiness(1, listOf(MissingField.PURPOSE, MissingField.VEHICLE), 0.50f),
                ReportReadiness(2, listOf(MissingField.CATEGORY), 0.75f),
                ReportReadiness(3, listOf(MissingField.DISTANCE), 0.75f),
                ReportReadiness(4, emptyList(), 1.0f),
                ReportReadiness(5, emptyList(), 1.0f),
            ),
            onReviewTrips = {},
        )
    }
}

@Preview(name = "Incomplete — single (plural check)", showBackground = true)
@Composable
private fun ReportReadinessCardSingleIncompletePreview() {
    MileageTrackerTheme {
        ReportReadinessCard(
            readinessList = listOf(
                ReportReadiness(1, listOf(MissingField.PURPOSE), 0.75f),
                ReportReadiness(2, emptyList(), 1.0f),
            ),
            onReviewTrips = {},
        )
    }
}

/**
 * Confirms the early-return behavior: when every trip is ready the card
 * renders nothing. Preview pane should be empty.
 */
@Preview(name = "All ready — hidden", showBackground = true)
@Composable
private fun ReportReadinessCardAllReadyPreview() {
    MileageTrackerTheme {
        ReportReadinessCard(
            readinessList = listOf(
                ReportReadiness(1, emptyList(), 1.0f),
                ReportReadiness(2, emptyList(), 1.0f),
            ),
            onReviewTrips = {},
        )
    }
}
