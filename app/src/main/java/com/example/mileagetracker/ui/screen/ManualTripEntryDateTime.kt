package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.util.DateFormats
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeRow(
    label: String,
    epochMs: Long,
    error: String?,
    onPickedMs: (Long) -> Unit,
) {
    val notSet = stringResource(R.string.manual_trip_not_set)
    val dateValue = if (epochMs > 0L) DateFormats.uiDateWithDayOfWeek(epochMs) else notSet
    val timeValue = if (epochMs > 0L) DateFormats.uiTime(epochMs) else notSet
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMs by remember { mutableLongStateOf(if (epochMs > 0L) epochMs else System.currentTimeMillis()) }

    DateTimeSelectionCard(
        label = label,
        dateValue = dateValue,
        timeValue = timeValue,
        error = error,
        onPickDate = { showDatePicker = true },
        onPickTime = { showTimePicker = true },
    )
    DatePickerDialogIfNeeded(
        visible = showDatePicker,
        epochMs = epochMs,
        pendingDateMs = pendingDateMs,
        onDismiss = { showDatePicker = false },
        onDatePicked = { mergedDateMs ->
            pendingDateMs = mergedDateMs
            onPickedMs(pendingDateMs)
        },
    )
    TimePickerDialogIfNeeded(
        visible = showTimePicker,
        label = label,
        epochMs = epochMs,
        pendingDateMs = pendingDateMs,
        onDismiss = { showTimePicker = false },
        onTimePicked = { hour, minute ->
            pendingDateMs = applySelectedTime(
                baseMs = if (epochMs > 0L) epochMs else pendingDateMs,
                hour = hour,
                minute = minute,
            )
            onPickedMs(pendingDateMs)
        },
    )
}

@Composable
private fun DateTimeSelectionCard(
    label: String,
    dateValue: String,
    timeValue: String,
    error: String?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.manual_trip_timestamp_label, label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = onPickDate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.manual_trip_select_date))
                }
                OutlinedButton(
                    onClick = onPickTime,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.manual_trip_select_time))
                }
            }
            DateTimeValueText(
                text = stringResource(R.string.manual_trip_date_value, dateValue),
            )
            DateTimeValueText(
                text = stringResource(R.string.manual_trip_time_value, timeValue),
            )
            error?.let { errorText ->
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DateTimeValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogIfNeeded(
    visible: Boolean,
    epochMs: Long,
    pendingDateMs: Long,
    onDismiss: () -> Unit,
    onDatePicked: (Long) -> Unit,
) {
    if (!visible) {
        return
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (epochMs > 0L) epochMs else null,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMs ->
                        onDatePicked(
                            applySelectedDate(
                                selectedMs = selectedMs,
                                pendingDateMs = pendingDateMs,
                            ),
                        )
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogIfNeeded(
    visible: Boolean,
    label: String,
    epochMs: Long,
    pendingDateMs: Long,
    onDismiss: () -> Unit,
    onTimePicked: (Int, Int) -> Unit,
) {
    if (!visible) {
        return
    }

    val initialCalendar = Calendar.getInstance().apply {
        timeInMillis = if (epochMs > 0L) epochMs else pendingDateMs
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCalendar.get(Calendar.MINUTE),
        is24Hour = false,
    )
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.manual_trip_select_time_title, label),
                    style = MaterialTheme.typography.titleMedium,
                )
                TimePicker(state = timePickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onTimePicked(timePickerState.hour, timePickerState.minute)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

private fun applySelectedDate(
    selectedMs: Long,
    pendingDateMs: Long,
): Long {
    val utc = TimeZone.getTimeZone("UTC")
    val dateCalendar = Calendar.getInstance(utc).apply { timeInMillis = selectedMs }
    val localCalendar = Calendar.getInstance().apply { timeInMillis = pendingDateMs }
    localCalendar.set(Calendar.YEAR, dateCalendar.get(Calendar.YEAR))
    localCalendar.set(Calendar.MONTH, dateCalendar.get(Calendar.MONTH))
    localCalendar.set(Calendar.DAY_OF_MONTH, dateCalendar.get(Calendar.DAY_OF_MONTH))
    return localCalendar.timeInMillis
}

private fun applySelectedTime(
    baseMs: Long,
    hour: Int,
    minute: Int,
): Long = Calendar.getInstance().apply {
    timeInMillis = baseMs
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
