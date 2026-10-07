package com.example.mileagetracker.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.mileagetracker.util.registerActivityTransitions

internal data class OnboardingPermissionController(
    val dialogStep: OnboardingPermissionStep?,
    val statusMessage: String?,
    val startAutomaticTrackingFlow: () -> Unit,
    val dismissDialog: () -> Unit,
    val confirmDialog: () -> Unit,
)

private data class OnboardingPermissionLaunchers(
    val launchForeground: () -> Unit,
    val launchBackground: () -> Unit,
    val launchActivityRecognition: () -> Unit,
)

internal enum class OnboardingPermissionStep(
    val title: String,
    val body: String,
) {
    FOREGROUND_LOCATION(
        title = "Allow Location While Using the App",
        body =
        "MileageTracker uses location to record trip start and end points " +
            "accurately when you choose automatic tracking.",
    ),
    BACKGROUND_LOCATION(
        title = "Allow Background Location",
        body =
        "Background location lets automatic trip detection keep working after " +
            "you leave the app. This is optional and can be enabled later in Settings.",
    ),
    ACTIVITY_RECOGNITION(
        title = "Allow Physical Activity Access",
        body =
        "Physical activity access tells MileageTracker when you start or stop " +
            "driving so it can start trip capture at the right time.",
    ),
}

@Composable
internal fun rememberOnboardingPermissionController(
    onFinish: () -> Unit,
): OnboardingPermissionController {
    val context = androidx.compose.ui.platform.LocalContext.current
    var dialogStep by remember { mutableStateOf<OnboardingPermissionStep?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val finishWithTrackingEnabled = {
        registerActivityTransitions(context)
        onFinish()
    }
    val advancePermissionFlow = {
        val nextStep = nextPermissionStep(context)
        if (nextStep == null) finishWithTrackingEnabled() else dialogStep = nextStep
    }
    val launchers = rememberOnboardingPermissionLaunchers(
        onForegroundGranted = advancePermissionFlow,
        onForegroundDenied = {
            dialogStep = null
            statusMessage =
                "Foreground location was not granted. You can continue in manual " +
                "mode and enable automatic tracking later in Settings."
        },
        onBackgroundGranted = advancePermissionFlow,
        onBackgroundDenied = {
            dialogStep = null
            statusMessage =
                "Background location was not granted. Automatic trip detection " +
                "will stay off until you enable it in Settings."
        },
        onActivityGranted = finishWithTrackingEnabled,
        onActivityDenied = {
            dialogStep = null
            statusMessage =
                "Activity recognition was not granted. Automatic trip detection " +
                "will stay off until you enable it in Settings."
        },
    )

    return OnboardingPermissionController(
        dialogStep = dialogStep,
        statusMessage = statusMessage,
        startAutomaticTrackingFlow = {
            statusMessage = null
            dialogStep = nextPermissionStep(context)
            if (dialogStep == null) finishWithTrackingEnabled()
        },
        dismissDialog = { dialogStep = null },
        confirmDialog = {
            when (dialogStep) {
                OnboardingPermissionStep.FOREGROUND_LOCATION ->
                    launchers.launchForeground()

                OnboardingPermissionStep.BACKGROUND_LOCATION ->
                    launchers.launchBackground()

                OnboardingPermissionStep.ACTIVITY_RECOGNITION ->
                    launchers.launchActivityRecognition()

                null -> Unit
            }
            dialogStep = null
        },
    )
}

@Composable
private fun rememberOnboardingPermissionLaunchers(
    onForegroundGranted: () -> Unit,
    onForegroundDenied: () -> Unit,
    onBackgroundGranted: () -> Unit,
    onBackgroundDenied: () -> Unit,
    onActivityGranted: () -> Unit,
    onActivityDenied: () -> Unit,
): OnboardingPermissionLaunchers {
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted =
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true &&
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) onForegroundGranted() else onForegroundDenied()
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onBackgroundGranted() else onBackgroundDenied()
    }
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onActivityGranted() else onActivityDenied()
    }

    return OnboardingPermissionLaunchers(
        launchForeground = {
            foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        },
        launchBackground = {
            launchSinglePermission(
                launcher = backgroundPermissionLauncher,
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        },
        launchActivityRecognition = {
            launchSinglePermission(
                launcher = activityPermissionLauncher,
                permission = Manifest.permission.ACTIVITY_RECOGNITION,
            )
        },
    )
}

private fun launchSinglePermission(
    launcher: ActivityResultLauncher<String>,
    permission: String,
) {
    launcher.launch(permission)
}

private fun nextPermissionStep(context: Context): OnboardingPermissionStep? = when {
    !hasForegroundLocationPermissions(context) -> OnboardingPermissionStep.FOREGROUND_LOCATION
    !hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
        OnboardingPermissionStep.BACKGROUND_LOCATION
    !hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ->
        OnboardingPermissionStep.ACTIVITY_RECOGNITION
    else -> null
}

private fun hasForegroundLocationPermissions(context: Context): Boolean =
    hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) &&
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
