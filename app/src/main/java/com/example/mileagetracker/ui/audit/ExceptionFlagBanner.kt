package com.example.mileagetracker.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.domain.model.ExceptionType
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke

/**
 * Inline banner displayed on [TripDetailScreen] when the current trip has
 * an active [TripException].
 *
 * Shows the exception type in plain language with a short explanatory
 * description, plus two actions:
 * - **Resolve** — navigates to the appropriate fix flow (caller handles).
 * - **Dismiss** — hides the banner for this session (caller handles).
 *
 * If [exception] is null or resolved, the composable renders nothing.
 *
 * Usage in TripDetailScreen:
 * ```kotlin
 * ExceptionFlagBanner(
 *     exception  = uiState.activeException,
 *     onResolve  = { navController.navigate("exceptionsDetail/${tripId}") },
 *     onDismiss  = { viewModel.dismissExceptionBanner() },
 * )
 * ```
 *
 * @param exception The active [TripException] to display, or null.
 * @param onResolve Called when the user taps the "Resolve" action button.
 * @param onDismiss Called when the user taps the "Dismiss" text button.
 * @param modifier  Standard Compose modifier forwarded to the outer [Card].
 */
@Composable
fun ExceptionFlagBanner(
    exception: TripException?,
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (exception == null || !exception.isActive) return

    val (icon, title, body) = exception.exceptionType.toBannerContent()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.thin),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ExceptionFlagIcon(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ExceptionFlagText(
                    title = title,
                    body = body,
                )
                ExceptionFlagActions(
                    onResolve = onResolve,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ExceptionFlagIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ExceptionFlagText(
    title: String,
    body: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun ExceptionFlagActions(
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onResolve,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Resolve")
        }
        TextButton(onClick = onDismiss) {
            Text(
                text = "Dismiss",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ── Banner content mapping ─────────────────────────────────────────────

private data class BannerContent(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private fun ExceptionType.toBannerContent(): BannerContent = when (this) {
    ExceptionType.DUPLICATE_FLAGGED ->
        BannerContent(
            icon = Icons.Default.ContentCopy,
            title = "This trip may be a duplicate",
            body = "A similar trip exists for overlapping times. Review and merge or keep both.",
        )
    ExceptionType.INCOMPLETE_DATA ->
        BannerContent(
            icon = Icons.Default.ErrorOutline,
            title = "Trip is missing required info",
            body = "Purpose, vehicle, category, or distance is not set. Fix to include in CRA reports.",
        )
    ExceptionType.IMPOSSIBLE_ROUTE ->
        BannerContent(
            icon = Icons.Default.Route,
            title = "Route data looks incorrect",
            body = "The recorded distance or trip duration seems implausible. Please verify the route.",
        )
    ExceptionType.MANUAL_OVERRIDE ->
        BannerContent(
            icon = Icons.Default.Flag,
            title = "Category override not confirmed",
            body = "You changed the auto-classification but haven't confirmed the trip. Tap Resolve to confirm.",
        )
    ExceptionType.NEEDS_REVIEW ->
        BannerContent(
            icon = Icons.Default.WarningAmber,
            title = "This trip needs review",
            body = "The system flagged this trip for manual attention before it can be finalised.",
        )
}
