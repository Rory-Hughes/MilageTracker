package com.example.mileagetracker.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.mileagetracker.domain.model.ExpenseCategory

@Composable
internal fun ScanReceiptButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.Icon(
            Icons.Default.DocumentScanner,
            contentDescription = null,
        )
        Text("  Scan Receipt")
    }
}

@Composable
internal fun AmountField(
    amountRaw: String,
    amountError: String?,
    onAmountChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = amountRaw,
        onValueChange = onAmountChanged,
        label = { Text("Amount (CAD)") },
        isError = amountError != null,
        supportingText = amountError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseCategoryDropdown(
    selectedCategory: ExpenseCategory,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCategorySelected: (ExpenseCategory) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = selectedCategory.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            ExpenseCategory.fixed.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName) },
                    onClick = {
                        onCategorySelected(category)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ExpenseDateField(
    dateLabel: String,
    onShowDatePicker: () -> Unit,
) {
    OutlinedTextField(
        value = dateLabel,
        onValueChange = {},
        readOnly = true,
        label = { Text("Date") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = onShowDatePicker) {
                Text("Change")
            }
        },
    )
}

@Composable
internal fun VendorField(
    vendorName: String,
    onVendorChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = vendorName,
        onValueChange = onVendorChanged,
        label = { Text("Vendor (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun GstHstField(
    gstHstRaw: String,
    onGstHstChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = gstHstRaw,
        onValueChange = onGstHstChanged,
        label = { Text("GST/HST Amount (optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun BusinessUseRow(
    isBusinessUse: Boolean,
    onBusinessUseChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Business use?", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = isBusinessUse,
            onCheckedChange = onBusinessUseChanged,
        )
    }
}

@Composable
internal fun NotesField(
    notes: String,
    onNotesChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChanged,
        label = { Text("Notes (optional)") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun CraInfoText(craNote: String) {
    Text(
        text = craNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SaveExpenseButton(
    editingId: Int,
    onSave: () -> Unit,
) {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (editingId == 0) "Save Expense" else "Update Expense")
    }
}
