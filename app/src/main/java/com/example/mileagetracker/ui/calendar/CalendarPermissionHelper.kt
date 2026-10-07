package com.example.mileagetracker.ui.calendar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Helpers for the `READ_CALENDAR` runtime permission.
 *
 * ## Permission states
 * - **Granted** – calendar queries can proceed immediately.
 * - **Should show rationale** – the user previously denied once; show a
 *   rationale dialog before re-requesting.
 * - **Permanently denied** – `shouldShowRequestPermissionRationale` returns
 *   `false` after a denial; we store a [PREF_PERMANENTLY_DENIED] flag in
 *   DataStore and suppress all future prompts to avoid pestering the user.
 *
 * ## Usage
 * ```kotlin
 * val launcher = rememberLauncherForActivityResult(
 *     ActivityResultContracts.RequestPermission()
 * ) { granted -> /* handle result */ }
 *
 * CalendarPermissionHelper.requestIfNeeded(
 *     context  = context,
 *     activity = activity,
 *     launcher = launcher,
 *     onAlreadyGranted = { /* proceed */ },
 *     onPermanentlyDenied = { /* show settings snackbar */ },
 * )
 * ```
 */
object CalendarPermissionHelper {

    private val Context.calendarPrefs by preferencesDataStore(name = "calendar_permission_prefs")

    private val PREF_PERMANENTLY_DENIED = booleanPreferencesKey("read_calendar_permanently_denied")

    val PERMISSION = Manifest.permission.READ_CALENDAR

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun isPermanentlyDenied(context: Context): Boolean = runBlocking {
        context.calendarPrefs.data
            .map { prefs -> prefs[PREF_PERMANENTLY_DENIED] ?: false }
            .first()
    }

    suspend fun markPermanentlyDenied(context: Context) {
        context.calendarPrefs.edit { prefs ->
            prefs[PREF_PERMANENTLY_DENIED] = true
        }
    }

    /**
     * Decides whether to request the permission, show a rationale, or do
     * nothing (if permanently denied or already granted).
     *
     * @param context              Application or activity context.
     * @param activity             The current [Activity] (needed for rationale check).
     * @param launcher             `ActivityResultLauncher` from `rememberLauncherForActivityResult`.
     * @param onAlreadyGranted     Called immediately if permission is already granted.
     * @param onShowRationale      Called when a rationale dialog should be shown before
     *                             re-requesting; the caller must invoke [launcher] after
     *                             the dialog is accepted.
     * @param onPermanentlyDenied  Called when the permission is permanently denied;
     *                             the caller should show a settings deep-link snackbar.
     */
    fun requestIfNeeded(
        context:             Context,
        activity:            Activity,
        launcher:            ActivityResultLauncher<String>,
        onAlreadyGranted:    () -> Unit,
        onShowRationale:     (launcher: ActivityResultLauncher<String>) -> Unit = { it.launch(PERMISSION) },
        onPermanentlyDenied: () -> Unit = {},
    ) {
        when {
            isGranted(context) -> onAlreadyGranted()
            isPermanentlyDenied(context) -> onPermanentlyDenied()
            ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION) ->
                onShowRationale(launcher)
            else -> launcher.launch(PERMISSION)
        }
    }
}
