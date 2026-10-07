package com.example.mileagetracker.domain.model

/**
 * Represents a single permission or system-setting diagnostic result
 * produced by [ValidateTrackingPermissionsUseCase].
 *
 * ## Sealed hierarchy
 * | Subclass                       | Condition that triggers it |
 * |--------------------------------|----------------------------|
 * | [AllClear]                     | All three checks passed |
 * | [BackgroundLocationMissing]    | `ACCESS_BACKGROUND_LOCATION` not granted |
 * | [BatteryOptimizationActive]    | App is NOT on the battery-optimisation exemption list |
 * | [NotificationPermissionMissing]| `POST_NOTIFICATIONS` not granted (Android 13+) |
 *
 * Each non-clear subclass carries:
 * - [TITLE]       — Short display name shown as the card header.
 * - [EXPLANATION] — Plain-language description of why it matters.
 * - [SETTINGSACTION] — An Android `Settings.*` or `Intent.*` action constant
 *                      the UI can fire to send the user directly to the
 *                      relevant system screen.
 */
sealed class PermissionDiagnostic {

    /** All permission and system-setting checks passed. No action required. */
    data object AllClear : PermissionDiagnostic()

    /**
     * `ACCESS_BACKGROUND_LOCATION` has not been granted.
     *
     * Without this permission the OS terminates GPS updates as soon as
     * the app is backgrounded, causing trips to be recorded as zero-km
     * or not recorded at all.
     *
     * **Settings action:** `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
     * — opens the app's permission detail screen where the user can change
     * location to "Allow all the time".
     */
    data object BackgroundLocationMissing : PermissionDiagnostic() {
        const val TITLE = "Background Location Off"
        const val EXPLANATION =
            "MileageTracker needs \"Allow all the time\" location access to record " +
                "trips while the app is in the background or the screen is off. " +
                "Without it, trips may be cut short or missed entirely."
        const val SETTINGSACTION = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }

    /**
     * The app is subject to battery optimisation (i.e. it is NOT on the
     * system's exemption list).
     *
     * On many OEM ROMs (Samsung, Xiaomi, Huawei) aggressive battery savers
     * kill background processes and GPS listeners, silently dropping trips.
     *
     * **Settings action:** `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     * — prompts the user with a system dialog to add this app to the
     * exemption list.  Must be accompanied by a `data` URI of the package.
     */
    data object BatteryOptimizationActive : PermissionDiagnostic() {
        const val TITLE = "Battery Optimization Active"
        const val EXPLANATION =
            "Your device's battery saver may stop MileageTracker from recording " +
                "trips in the background. Tap \"Fix\" to review battery settings " +
                "and allow reliable trip recording."
        const val SETTINGSACTION = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    }

    /**
     * `POST_NOTIFICATIONS` has not been granted (Android 13+).
     *
     * Without this permission the service cannot display its foreground
     * notification, which means it cannot run as a foreground service on
     * Android 9+, causing trips to be silently dropped.
     *
     * **Settings action:** `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
     * — opens the app detail screen where the user can enable notifications.
     */
    data object NotificationPermissionMissing : PermissionDiagnostic() {
        const val TITLE = "Notifications Disabled"
        const val EXPLANATION =
            "Notifications must be enabled for MileageTracker to run its " +
                "trip-recording service in the foreground. Without them, Android " +
                "may stop the service and trips will be missed."
        const val SETTINGSACTION = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS }

    data class ActivityTransitionRegistrationFailed(
        val reason: String,
    ) : PermissionDiagnostic() {
        companion object {
            const val TITLE = "Automatic Detection Registration Failed"
            const val EXPLANATION =
                "Automatic trip detection is enabled but Android could not register the motion " +
                    "listener. Review the app permissions, then reopen MileageTracker to retry."
            const val SETTINGSACTION = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
    }
}
