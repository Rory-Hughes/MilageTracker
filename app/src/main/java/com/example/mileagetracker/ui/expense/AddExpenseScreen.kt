package com.example.mileagetracker.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.navigation.Screen
import com.example.mileagetracker.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Add / Edit expense form screen.
 *
 * OCR results are read from the current back stack entry and forwarded into
 * [VehicleExpenseViewModel] so that scanned receipts prefill the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: NavController,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: VehicleExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val dateLabel = remember(state.dateMillis) {
        Instant.ofEpochMilli(state.dateMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

    ConsumeOcrReceiptResults(
        savedStateHandle = savedStateHandle,
        onAmountChanged = viewModel.onAmountChanged,
        onVendorChanged = viewModel.onVendorChanged,
        onDateChanged = viewModel.onDateChanged,
        onGstHstChanged = viewModel.onGstHstChanged,
    )

    Scaffold(
        topBar = {
            ExpenseTopBar(
                editingId = state.editingId,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        ExpenseFormContent(
            navController = navController,
            state = state,
            dateLabel = dateLabel,
            categoryExpanded = categoryExpanded,
            onCategoryExpandedChange = { categoryExpanded = it },
            onShowDatePicker = { showDatePicker = true },
            onAmountChanged = viewModel.onAmountChanged,
            onCategorySelected = viewModel.onCategorySelected,
            onVendorChanged = viewModel.onVendorChanged,
            onGstHstChanged = viewModel.onGstHstChanged,
            onBusinessUseChanged = viewModel.onIsBusinessUseToggled,
            onNotesChanged = viewModel.onNotesChanged,
            onSave = { viewModel.saveExpense(onSaved) },
            innerPadding = innerPadding,
        )
    }

    if (showDatePicker) {
        ExpenseDatePickerDialog(
            initialDateMillis = state.dateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = viewModel.onDateChanged,
        )
    }
}

@Composable
private fun ConsumeOcrReceiptResults(
    savedStateHandle: SavedStateHandle?,
    onAmountChanged: (String) -> Unit,
    onVendorChanged: (String) -> Unit,
    onDateChanged: (Long) -> Unit,
    onGstHstChanged: (String) -> Unit,
) {
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(Screen.OCR_AMOUNT, null)?.collect { raw ->
            if (!raw.isNullOrBlank()) onAmountChanged(raw)
        }
    }
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(Screen.OCR_VENDOR, null)?.collect { vendor ->
            if (!vendor.isNullOrBlank()) onVendorChanged(vendor)
        }
    }
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<Long?>(Screen.OCR_DATE, null)?.collect { dateMs ->
            if (dateMs != null) onDateChanged(dateMs)
        }
    }
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(Screen.OCR_GST, null)?.collect { gst ->
            if (!gst.isNullOrBlank()) onGstHstChanged(gst)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseTopBar(
    editingId: Int,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(if (editingId == 0) "Add Expense" else "Edit Expense") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
internal fun ExpenseFormContent(
    navController: NavController,
    state: ExpenseFormState,
    dateLabel: String,
    categoryExpanded: Boolean,
    onCategoryExpandedChange: (Boolean) -> Unit,
    onShowDatePicker: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (ExpenseCategory) -> Unit,
    onVendorChanged: (String) -> Unit,
    onGstHstChanged: (String) -> Unit,
    onBusinessUseChanged: (Boolean) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    innerPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = Spacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        ScanReceiptButton(
            onClick = { navController.navigate(Screen.OcrReceipt.route) },
        )
        AmountField(
            amountRaw = state.amountRaw,
            amountError = state.amountError,
            onAmountChanged = onAmountChanged,
        )
        ExpenseCategoryDropdown(
            selectedCategory = state.category,
            expanded = categoryExpanded,
            onExpandedChange = onCategoryExpandedChange,
            onCategorySelected = onCategorySelected,
        )
        ExpenseDateField(
            dateLabel = dateLabel,
            onShowDatePicker = onShowDatePicker,
        )
        VendorField(
            vendorName = state.vendorName,
            onVendorChanged = onVendorChanged,
        )
        GstHstField(
            gstHstRaw = state.gstHstRaw,
            onGstHstChanged = onGstHstChanged,
        )
        BusinessUseRow(
            isBusinessUse = state.isBusinessUse,
            onBusinessUseChanged = onBusinessUseChanged,
        )
        NotesField(
            notes = state.notes,
            onNotesChanged = onNotesChanged,
        )
        CraInfoText(craNote = state.category.craNote)
        SaveExpenseButton(
            editingId = state.editingId,
            onSave = onSave,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDatePickerDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let(onDateSelected)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
