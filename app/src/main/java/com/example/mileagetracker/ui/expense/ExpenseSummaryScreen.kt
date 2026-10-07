package com.example.mileagetracker.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val CAD_FORMAT: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.CANADA)

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSummaryScreen(
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onEditItem: (VehicleExpense) -> Unit,
    viewModel: VehicleExpenseViewModel = hiltViewModel(),
) {
    val expenses by viewModel.expenses.collectAsState()
    val totalAmount = expenses.sumOf { it.amountCad }
    val totalDeductible = expenses.sumOf { it.businessUseAmount }

    Scaffold(
        topBar = {
            ExpenseSummaryTopBar(onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        },
    ) { innerPadding ->
        ExpenseSummaryContent(
            expenses = expenses,
            totalAmount = totalAmount,
            totalDeductible = totalDeductible,
            onEditItem = onEditItem,
            onDeleteExpense = viewModel.deleteExpense,
            innerPadding = innerPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseSummaryTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Vehicle Expenses") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
private fun ExpenseSummaryContent(
    expenses: List<VehicleExpense>,
    totalAmount: Double,
    totalDeductible: Double,
    onEditItem: (VehicleExpense) -> Unit,
    onDeleteExpense: (Int) -> Unit,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Spacer(Modifier.height(Spacing.sm))
            SummaryCard(
                totalAmount = totalAmount,
                totalDeductible = totalDeductible,
            )
        }

        if (expenses.isEmpty()) {
            item {
                EmptyExpenseState()
            }
        }

        items(expenses, key = { it.id }) { expense ->
            ExpenseRow(
                expense = expense,
                onEdit = { onEditItem(expense) },
                onDelete = { onDeleteExpense(expense.id) },
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun EmptyExpenseState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No expenses logged yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Tap + to add your first expense.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryCard(
    totalAmount: Double,
    totalDeductible: Double,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = "Year-to-date totals",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
            SummaryRow(label = "Total expenses", value = CAD_FORMAT.format(totalAmount))
            SummaryRow(
                label = "Est. CRA deductible",
                value = CAD_FORMAT.format(totalDeductible),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: VehicleExpense,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateLabel = Instant.ofEpochMilli(expense.date)
        .atZone(ZoneId.systemDefault())
        .format(DATE_FORMAT)

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = expense.category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = expenseMetadataLabel(
                        dateLabel = dateLabel,
                        vendorName = expense.vendorName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!expense.notes.isNullOrBlank()) {
                    Text(
                        text = expense.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ExpenseAmounts(expense = expense)
            ExpenseActions(
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun ExpenseAmounts(expense: VehicleExpense) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = CAD_FORMAT.format(expense.amountCad),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (expense.businessUseAmount > 0.0) {
            Text(
                text = "Deductible: ${CAD_FORMAT.format(expense.businessUseAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ExpenseActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit expense")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete expense")
        }
    }
}

private fun expenseMetadataLabel(
    dateLabel: String,
    vendorName: String?,
): String = if (vendorName != null) {
    "$dateLabel  •  $vendorName"
} else {
    dateLabel
}
