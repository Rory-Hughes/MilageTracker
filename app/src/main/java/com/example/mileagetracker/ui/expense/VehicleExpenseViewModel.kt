package com.example.mileagetracker.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import com.example.mileagetracker.domain.usecase.expense.GetVehicleExpensesUseCase
import com.example.mileagetracker.domain.usecase.expense.SaveVehicleExpenseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * ViewModel for [AddExpenseScreen] and [ExpenseSummaryScreen].
 *
 * ## State
 * - [uiState]          — form-entry state for [AddExpenseScreen].
 * - [businessPercent]  — mutable business-use % supplied by the host screen
 *                        (e.g. from [GetAnnualVehicleSummaryUseCase]).
 * - [expenses]         — reactive list of [VehicleExpense] with
 *                        [VehicleExpense.businessUseAmount] injected.
 *
 * ## Navigation
 * `vehicleId` and `year` are resolved from [SavedStateHandle] so that both
 * screens can be deep-linked.
 *
 * ## businessPercent
 * [VehicleEntity] does not store a business-use percentage — it is a computed
 * value derived from the annual trip log.  The host screen (or another
 * ViewModel) should call [setBusinessPercent] once the value is available
 * (e.g. from [GetAnnualVehicleSummaryUseCase] or a manual user entry).
 * Defaults to 0.0 until set, so expenses will show a deductible of \$0.00.
 */
@HiltViewModel
class VehicleExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveUseCase: SaveVehicleExpenseUseCase,
    private val getUseCase: GetVehicleExpensesUseCase,
    private val repository: VehicleExpenseRepository,
) : ViewModel() {

    private val vehicleId: Int = checkNotNull(savedStateHandle["vehicleId"])
    private val year: Int = savedStateHandle["year"] ?: LocalDate.now().year

    // ── Business-use percentage ────────────────────────────────────────────────

    /**
     * Vehicle business-use percentage in [0.0, 100.0].
     *
     * Update this after loading [VehicleAnnualSummary.businessPercent] from
     * [GetAnnualVehicleSummaryUseCase] so that [expenses] reflects the correct
     * CRA deductible amounts.
     */
    private val _businessPercent = MutableStateFlow(0.0)
    val businessPercent: StateFlow<Double> = _businessPercent.asStateFlow()

    val setBusinessPercent: (Double) -> Unit = { percent ->
        _businessPercent.value = percent.coerceIn(0.0, 100.0)
    }

    // ── Expense list ────────────────────────────────────────────────────────────

    /**
     * Reactive stream of expenses for [vehicleId] / [year], automatically
     * re-emitted when [businessPercent] changes so that the deductible
     * column in [ExpenseSummaryScreen] stays in sync.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<VehicleExpense>> = _businessPercent
        .flatMapLatest { pct -> getUseCase(vehicleId, year, pct) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ── Add / Edit form state ────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ExpenseFormState(vehicleId = vehicleId))
    val uiState: StateFlow<ExpenseFormState> = _uiState.asStateFlow()

    val onAmountChanged: (String) -> Unit = { raw ->
        _uiState.update { it.copy(amountRaw = raw, amountError = null) }
    }

    val onCategorySelected: (ExpenseCategory) -> Unit = { category ->
        _uiState.update { it.copy(category = category) }
    }

    val onDateChanged: (Long) -> Unit = { epochMillis ->
        _uiState.update { it.copy(dateMillis = epochMillis) }
    }

    val onVendorChanged: (String) -> Unit = { vendor ->
        _uiState.update { it.copy(vendorName = vendor) }
    }

    val onNotesChanged: (String) -> Unit = { notes ->
        _uiState.update { it.copy(notes = notes) }
    }

    val onGstHstChanged: (String) -> Unit = { raw ->
        _uiState.update { it.copy(gstHstRaw = raw) }
    }

    val onIsBusinessUseToggled: (Boolean) -> Unit = { value ->
        _uiState.update { it.copy(isBusinessUse = value) }
    }

    val onReceiptUriSet: (String?) -> Unit = { uri ->
        _uiState.update { it.copy(receiptAttachmentUri = uri) }
    }

    val loadForEdit: (VehicleExpense) -> Unit = { expense ->
        _uiState.update {
            ExpenseFormState(
                editingId = expense.id,
                vehicleId = expense.vehicleId,
                amountRaw = expense.amountCad.toString(),
                category = expense.category,
                dateMillis = expense.date,
                vendorName = expense.vendorName ?: "",
                notes = expense.notes ?: "",
                gstHstRaw = expense.gstHstAmount?.toString() ?: "",
                isBusinessUse = expense.isBusinessUse,
                receiptAttachmentUri = expense.receiptAttachmentUri,
            )
        }
    }

    val resetForm: () -> Unit = {
        _uiState.update { ExpenseFormState(vehicleId = vehicleId) }
    }

    // ── Save / delete ──────────────────────────────────────────────────────────

    val saveExpense: (() -> Unit) -> Unit = { onSuccess ->
        val state = _uiState.value
        val amount = state.amountRaw.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { it.copy(amountError = "Enter a valid amount greater than zero.") }
        } else {
            viewModelScope.launch {
                val expense = VehicleExpense(
                    id = state.editingId,
                    vehicleId = state.vehicleId,
                    category = state.category,
                    amountCad = amount,
                    date = state.dateMillis,
                    vendorName = state.vendorName.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    receiptAttachmentUri = state.receiptAttachmentUri,
                    gstHstAmount = state.gstHstRaw.toDoubleOrNull(),
                    isBusinessUse = state.isBusinessUse,
                )
                val result = saveUseCase(expense)
                if (result is SaveVehicleExpenseUseCase.Result.Success) {
                    resetForm()
                    onSuccess()
                } else if (result is SaveVehicleExpenseUseCase.Result.ValidationError) {
                    _uiState.update { it.copy(amountError = result.reason) }
                }
            }
        }
    }

    val deleteExpense: (Int) -> Unit = { id ->
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}

/**
 * Immutable state for the add / edit expense form.
 *
 * @param editingId  0 means a new expense is being created; > 0 means edit.
 */
data class ExpenseFormState(
    val editingId: Int = 0,
    val vehicleId: Int,
    val amountRaw: String = "",
    val amountError: String? = null,
    val category: ExpenseCategory = ExpenseCategory.Fuel,
    val dateMillis: Long = LocalDate.now()
        .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    val vendorName: String = "",
    val notes: String = "",
    val gstHstRaw: String = "",
    val isBusinessUse: Boolean = true,
    val receiptAttachmentUri: String? = null,
)
