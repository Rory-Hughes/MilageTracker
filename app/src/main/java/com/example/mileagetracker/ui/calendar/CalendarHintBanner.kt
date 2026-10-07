package com.example.mileagetracker.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.util.DateFormats

/**
 * Inline banner shown on `TripDetailScreen` when the trip has no purpose set
 * and at least one calendar hint is available.
 *
 * Displays the top hint's event title and time, with a "Use this" button that
 * applies the suggestion and a "Dismiss" button that hides the banner for this
 * screen session.
 *
 * The composable is animated in/out with a slide + fade so it doesn't startle
 * the user when it appears after the calendar query resolves.
 *
 * @param hint       The top-ranked [CalendarHint] to surface.
 * @param onUseHint  Called when the user taps "Use this"; passes the
 *                   [CalendarHint.suggestedPurpose] string.
 * @param onDismiss  Called when the user taps "Dismiss"; the host should clear
 *                   the hint list so the banner disappears.
 * @param modifier   Optional modifier applied to the outer [Surface].
 */
@Composable
fun CalendarHintBanner(
    hint: CalendarHint,
    onUseHint: (purpose: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val eventTime = remember(hint.startMs, locale) {
        DateFormats.uiTime(hint.startMs, locale)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = Stroke.thin,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            CalendarHintHeader()
            Spacer(modifier = Modifier.height(Spacing.xs))
            CalendarHintDetails(
                eventTitle = hint.eventTitle,
                eventTime = eventTime,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            CalendarHintActions(
                onUseHint = { onUseHint(hint.suggestedPurpose) },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun CalendarHintHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Calendar hint",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CalendarHintDetails(
    eventTitle: String,
    eventTime: String,
) {
    Text(
        text = eventTitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Text(
        text = eventTime,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
    )
}

@Composable
private fun CalendarHintActions(
    onUseHint: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledTonalButton(
            onClick = onUseHint,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = "Use this")
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = "Dismiss")
        }
    }
}

/**
 * Animated wrapper that slides [CalendarHintBanner] in from the top when
 * [visible] transitions from false to true, and slides it out when dismissed.
 *
 * Use this variant in [TripDetailScreen] so the banner doesn't cause an abrupt
 * layout shift.
 */
@Composable
fun AnimatedCalendarHintBanner(
    visible: Boolean,
    hint: CalendarHint?,
    onUseHint: (purpose: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && hint != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        hint?.let {
            CalendarHintBanner(
                hint = it,
                onUseHint = onUseHint,
                onDismiss = onDismiss,
            )
        }
    }
}
