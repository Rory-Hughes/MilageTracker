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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun SuggestionBannerCard(
    suggestion: TripSuggestion,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        ) {
            SuggestionHeader(onDismiss = onDismiss)
            Spacer(Modifier.height(Spacing.xs))
            SuggestionBody(
                bodyText = suggestionBodyText(suggestion),
                confidenceText = suggestionConfidenceText(suggestion),
            )
            Spacer(Modifier.height(Spacing.sm))
            SuggestionActions(
                onDismiss = onDismiss,
                onApply = onApply,
            )
        }
    }
}

@Composable
private fun SuggestionHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "Suggested classification",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss suggestion",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SuggestionBody(
    bodyText: String,
    confidenceText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = confidenceText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionActions(
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) {
            Text("Dismiss")
        }
        Spacer(Modifier.width(Spacing.sm))
        FilledTonalButton(
            onClick = onApply,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text("Apply")
        }
    }
}

private fun suggestionBodyText(suggestion: TripSuggestion): String {
    val categoryLabel = suggestion.suggestedCategory.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
    return if (!suggestion.suggestedPurpose.isNullOrBlank()) {
        "$categoryLabel • ${suggestion.suggestedPurpose}"
    } else {
        categoryLabel
    }
}

private fun suggestionConfidenceText(suggestion: TripSuggestion): String =
    "${(suggestion.matchConfidence * 100).roundToInt()}% match"
