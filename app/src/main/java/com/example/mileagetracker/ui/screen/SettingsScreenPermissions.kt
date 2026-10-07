package com.example.mileagetracker.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.calendar.CalendarPermissionHelper
import com.example.mileagetracker.ui.viewmodel.SettingsUiState
import kotlinx.coroutines.launch

internal data class SettingsPermissionState(
    val tracking: TrackingPermissionUiState,
    val calendar: CalendarPermissionUiState,
)

internal data class TrackingPermissionUiState(
    val statusText: String,
    val permissionMessage: String?,
    val onToggle: (Boolean) -> Unit,
)

internal data class CalendarPermissionUiState(
    val message: String?,
    val showRationale: Boolean,
    val onToggle: (Boolean) -> Unit,
    val onDismissRationale: () -> Unit,
    val onConfirmRationale: () -> Unit,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun rememberSettingsPermissionState(
    state: SettingsUiState,
    onAutomaticTrackingEnabledChange: (Boolean) -> Unit,
    onCalendarHintsEnabledChange: (Boolean) -> Unit,
): SettingsPermissionState = SettingsPermissionState(
    tracking = rememberTrackingPermissionUiState(
        state = state,
        onAutomaticTrackingEnabledChange = onAutomaticTrackingEnabledChange,
    ),
    calendar = rememberCalendarPermissionUiState(
        onCalendarHintsEnabledChange = onCalendarHintsEnabledChange,
    ),
)

@Composable
internal fun CalendarPermissionRationaleDialog(
    state: CalendarPermissionUiState,
) {
    if (!state.showRationale) return
    AlertDialog(
        onDismissRequest = state.onDismissRationale,
        title = { Text(stringResource(R.string.settings_calendar_permission_title)) },
        text = { Text(stringResource(R.string.settings_calendar_permission_rationale)) },
        confirmButton = {
            TextButton(onClick = state.onConfirmRationale) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = state.onDismissRationale) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberTrackingPermissionUiState(
    state: SettingsUiState,
    onAutomaticTrackingEnabledChange: (Boolean) -> Unit,
): TrackingPermissionUiState {
    var trackingEnablePending by remember { mutableStateOf(false) }
    var trackingPermissionMessage by remember { mutableStateOf<String?>(null) }
    val requiredText = stringResource(R.string.settings_tracking_permission_required)
    val foregroundPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    )
    val backgroundPermission = rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val activityRecognitionPermission = rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    val allPermissionsGranted = foregroundPermissions.allPermissionsGranted &&
        backgroundPermission.status.isGranted &&
        activityRecognitionPermission.status.isGranted

    LaunchedEffect(allPermissionsGranted, trackingEnablePending) {
        if (trackingEnablePending && allPermissionsGranted) {
            trackingEnablePending = false
            trackingPermissionMessage = null
            onAutomaticTrackingEnabledChange(true)
        }
    }

    return TrackingPermissionUiState(
        statusText = trackingStatusText(state, trackingEnablePending),
        permissionMessage = trackingPermissionMessage,
        onToggle = { enabled ->
            if (!enabled) {
                trackingEnablePending = false
                trackingPermissionMessage = null
                onAutomaticTrackingEnabledChange(false)
            } else if (allPermissionsGranted) {
                trackingEnablePending = false
                trackingPermissionMessage = null
                onAutomaticTrackingEnabledChange(true)
            } else {
                trackingEnablePending = true
                trackingPermissionMessage = requiredText
                foregroundPermissions.launchMultiplePermissionRequest()
                backgroundPermission.launchPermissionRequest()
                activityRecognitionPermission.launchPermissionRequest()
            }
        },
    )
}

@Composable
private fun rememberCalendarPermissionUiState(
    onCalendarHintsEnabledChange: (Boolean) -> Unit,
): CalendarPermissionUiState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    var showRationale by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val requiredText = stringResource(R.string.settings_calendar_permission_required)
    val deniedText = stringResource(R.string.settings_calendar_permission_denied)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        handleCalendarPermissionResult(
            granted = granted,
            context = context,
            activity = activity,
            requiredText = requiredText,
            deniedText = deniedText,
            coroutineScope = coroutineScope,
            onMessageChanged = { message = it },
            onCalendarHintsEnabledChange = onCalendarHintsEnabledChange,
        )
    }

    return CalendarPermissionUiState(
        message = message,
        showRationale = showRationale,
        onToggle = { enabled ->
            if (!enabled) {
                message = null
                onCalendarHintsEnabledChange(false)
            } else {
                requestCalendarPermission(
                    context = context,
                    activity = activity,
                    launcher = launcher,
                    requiredText = requiredText,
                    deniedText = deniedText,
                    onShowRationale = { showRationale = true },
                    onMessageChanged = { message = it },
                    onCalendarHintsEnabledChange = onCalendarHintsEnabledChange,
                )
            }
        },
        onDismissRationale = { showRationale = false },
        onConfirmRationale = {
            showRationale = false
            launcher.launch(CalendarPermissionHelper.PERMISSION)
        },
    )
}

@Composable
private fun trackingStatusText(
    state: SettingsUiState,
    trackingEnablePending: Boolean,
): String = when {
    state.automaticTrackingEnabled && state.automaticTrackingRegistered ->
        stringResource(R.string.settings_tracking_background_status_on)
    trackingEnablePending ->
        stringResource(R.string.settings_tracking_background_status_pending)
    else ->
        stringResource(R.string.settings_tracking_background_status_off)
}

private fun requestCalendarPermission(
    context: Context,
    activity: Activity?,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    requiredText: String,
    deniedText: String,
    onShowRationale: () -> Unit,
    onMessageChanged: (String?) -> Unit,
    onCalendarHintsEnabledChange: (Boolean) -> Unit,
) {
    when {
        CalendarPermissionHelper.isGranted(context) -> {
            onMessageChanged(null)
            onCalendarHintsEnabledChange(true)
        }

        activity == null -> {
            onMessageChanged(requiredText)
            onCalendarHintsEnabledChange(false)
        }

        else -> {
            CalendarPermissionHelper.requestIfNeeded(
                context = context,
                activity = activity,
                launcher = launcher,
                onAlreadyGranted = {
                    onMessageChanged(null)
                    onCalendarHintsEnabledChange(true)
                },
                onShowRationale = { onShowRationale() },
                onPermanentlyDenied = {
                    onMessageChanged(deniedText)
                    onCalendarHintsEnabledChange(false)
                },
            )
        }
    }
}

private fun handleCalendarPermissionResult(
    granted: Boolean,
    context: Context,
    activity: Activity?,
    requiredText: String,
    deniedText: String,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onMessageChanged: (String?) -> Unit,
    onCalendarHintsEnabledChange: (Boolean) -> Unit,
) {
    if (granted) {
        onMessageChanged(null)
        onCalendarHintsEnabledChange(true)
        return
    }

    val shouldShowRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, CalendarPermissionHelper.PERMISSION)
    } == true
    if (!shouldShowRationale) {
        coroutineScope.launch {
            CalendarPermissionHelper.markPermanentlyDenied(context)
        }
        onMessageChanged(deniedText)
    } else {
        onMessageChanged(requiredText)
    }
    onCalendarHintsEnabledChange(false)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
