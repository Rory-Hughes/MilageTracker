package com.example.mileagetracker.ui.policy

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.mileagetracker.domain.model.PolicyTemplate

/**
 * Reusable dropdown composable that lets the user select a [PolicyTemplate] from a list.
 *
 * @param policies        All available [PolicyTemplate]s to display.
 * @param selectedId      The currently selected policy's id, or null for "None".
 * @param onPolicySelected Callback with the selected [PolicyTemplate], or null for "None".
 * @param label           Label text shown on the dropdown field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyPickerDropdown(
    policies: List<PolicyTemplate>,
    selectedId: String?,
    onPolicySelected: (PolicyTemplate?) -> Unit,
    label: String = "Policy Template"
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = policies.firstOrNull { it.id == selectedId }?.name ?: "None"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = androidx.compose.ui.Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onPolicySelected(null)
                    expanded = false
                }
            )
            policies.forEach { policy ->
                DropdownMenuItem(
                    text = { Text(policy.name) },
                    onClick = {
                        onPolicySelected(policy)
                        expanded = false
                    }
                )
            }
        }
    }
}
