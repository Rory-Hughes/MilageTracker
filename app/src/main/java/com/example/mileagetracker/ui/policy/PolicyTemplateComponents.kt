package com.example.mileagetracker.ui.policy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.ui.theme.Spacing
import java.util.UUID

@Composable
internal fun PolicyTemplateRow(
    policy: PolicyTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rateLabel = when (policy.rateMode) {
        RateMode.CRA_STANDARD -> "CRA standard rate"
        RateMode.CUSTOM -> "${policy.customRateCentsPerKm} cents/km"
    }
    val categoryLabel = if (policy.eligibleCategories.isEmpty()) {
        "All categories"
    } else {
        policy.eligibleCategories.joinToString(", ")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = policy.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(Spacing.xs))
                Text(text = rateLabel, style = MaterialTheme.typography.bodySmall)
                Text(text = categoryLabel, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete policy")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PolicyEditorSheet(
    initial: PolicyTemplate,
    onSave: (PolicyTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var rateMode by remember { mutableStateOf(initial.rateMode) }
    var customRate by remember { mutableStateOf(initial.customRateCentsPerKm?.toString() ?: "") }
    var excludeCommute by remember { mutableStateOf(initial.excludeCommute) }
    var requirePurpose by remember { mutableStateOf(initial.requirePurpose) }
    var requireAttachment by remember { mutableStateOf(initial.requireAttachment) }
    var maxDailyKm by remember { mutableStateOf(initial.maxDailyKm?.toString() ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var rateError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        PolicyEditorSheetContent(
            name = name,
            rateMode = rateMode,
            customRate = customRate,
            excludeCommute = excludeCommute,
            requirePurpose = requirePurpose,
            requireAttachment = requireAttachment,
            maxDailyKm = maxDailyKm,
            nameError = nameError,
            rateError = rateError,
            onNameChange = {
                name = it
                nameError = false
            },
            onRateModeChange = { rateMode = it },
            onCustomRateChange = {
                customRate = it
                rateError = false
            },
            onExcludeCommuteChange = { excludeCommute = it },
            onRequirePurposeChange = { requirePurpose = it },
            onRequireAttachmentChange = { requireAttachment = it },
            onMaxDailyKmChange = { maxDailyKm = it },
            onDismiss = onDismiss,
            onSave = {
                nameError = name.isBlank()
                rateError = rateMode == RateMode.CUSTOM &&
                    (customRate.toIntOrNull() ?: 0) <= 0
                if (!nameError && !rateError) {
                    onSave(
                        initial.copy(
                            id = if (initial.id.isBlank()) UUID.randomUUID().toString() else initial.id,
                            name = name.trim(),
                            rateMode = rateMode,
                            customRateCentsPerKm = customRate.toIntOrNull(),
                            excludeCommute = excludeCommute,
                            requirePurpose = requirePurpose,
                            requireAttachment = requireAttachment,
                            maxDailyKm = maxDailyKm.toIntOrNull(),
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun PolicyEditorSheetContent(
    name: String,
    rateMode: RateMode,
    customRate: String,
    excludeCommute: Boolean,
    requirePurpose: Boolean,
    requireAttachment: Boolean,
    maxDailyKm: String,
    nameError: Boolean,
    rateError: Boolean,
    onNameChange: (String) -> Unit,
    onRateModeChange: (RateMode) -> Unit,
    onCustomRateChange: (String) -> Unit,
    onExcludeCommuteChange: (Boolean) -> Unit,
    onRequirePurposeChange: (Boolean) -> Unit,
    onRequireAttachmentChange: (Boolean) -> Unit,
    onMaxDailyKmChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Edit Policy Template", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Policy Name *") },
            isError = nameError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PolicyRateModeSection(
            rateMode = rateMode,
            customRate = customRate,
            rateError = rateError,
            onRateModeChange = onRateModeChange,
            onCustomRateChange = onCustomRateChange,
        )
        OutlinedTextField(
            value = maxDailyKm,
            onValueChange = onMaxDailyKmChange,
            label = { Text("Max Daily Km (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PolicyRequirementsSection(
            excludeCommute = excludeCommute,
            requirePurpose = requirePurpose,
            requireAttachment = requireAttachment,
            onExcludeCommuteChange = onExcludeCommuteChange,
            onRequirePurposeChange = onRequirePurposeChange,
            onRequireAttachmentChange = onRequireAttachmentChange,
        )
        PolicySheetActions(
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
}

@Composable
private fun PolicyRateModeSection(
    rateMode: RateMode,
    customRate: String,
    rateError: Boolean,
    onRateModeChange: (RateMode) -> Unit,
    onCustomRateChange: (String) -> Unit,
) {
    Text("Rate Mode", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = rateMode == RateMode.CRA_STANDARD,
            onClick = { onRateModeChange(RateMode.CRA_STANDARD) },
            label = { Text("CRA Standard") },
        )
        FilterChip(
            selected = rateMode == RateMode.CUSTOM,
            onClick = { onRateModeChange(RateMode.CUSTOM) },
            label = { Text("Custom") },
        )
    }
    if (rateMode == RateMode.CUSTOM) {
        OutlinedTextField(
            value = customRate,
            onValueChange = onCustomRateChange,
            label = { Text("Rate (cents/km) *") },
            isError = rateError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PolicyRequirementsSection(
    excludeCommute: Boolean,
    requirePurpose: Boolean,
    requireAttachment: Boolean,
    onExcludeCommuteChange: (Boolean) -> Unit,
    onRequirePurposeChange: (Boolean) -> Unit,
    onRequireAttachmentChange: (Boolean) -> Unit,
) {
    PolicyRequirementRow(
        checked = excludeCommute,
        label = "Exclude commute trips",
        onCheckedChange = onExcludeCommuteChange,
    )
    PolicyRequirementRow(
        checked = requirePurpose,
        label = "Require trip purpose",
        onCheckedChange = onRequirePurposeChange,
    )
    PolicyRequirementRow(
        checked = requireAttachment,
        label = "Require attachment",
        onCheckedChange = onRequireAttachmentChange,
    )
}

@Composable
private fun PolicyRequirementRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun PolicySheetActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.width(Spacing.sm))
        Button(onClick = onSave) { Text("Save") }
    }
}
