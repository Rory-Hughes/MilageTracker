package com.example.mileagetracker.domain.usecase.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import com.example.mileagetracker.service.ActivityTransitionRegistrar
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Checks the three system-level prerequisites for reliable background
 * trip recording and returns an ordered list of [PermissionDiagnostic]s.
 *
 * ## Checks performed (in order)
 * 1. **Background location** — `ACCESS_BACKGROUND_LOCATION` granted.
 * 2. **Battery optimisation exemption** — app is on the system's
 *    [PowerManager.isIgnoringBatteryOptimizations] whitelist.
 *    Only checked on API 23+ where the API exists.
 * 3. **Notification permission** — `POST_NOTIFICATIONS` granted
 *    (Android 13+ only; automatically passes on lower API levels).
 *
 * ## Return contract
 * - Returns a **non-empty list** containing every failing check.
 * - If all checks pass, returns `listOf(PermissionDiagnostic.AllClear)`.
 * - The list is ordered: background location first, battery second,
 *   notification third, so the UI can display them in priority order.
 *
 * ## Threading
 * Pure in-memory checks; safe to call on any dispatcher including Main.
 * No IO or database access is performed.
 *
 * ## Usage
 * ```kotlin
 * val diagnostics = validateTrackingPermissionsUseCase()
 * if (diagnostics.none { it is PermissionDiagnostic.AllClear }) {
 *     // show banner
 * }
 * ```
 */
class ValidateTrackingPermissionsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    operator fun invoke(): List<PermissionDiagnostic> {
        val issues = mutableListOf<PermissionDiagnostic>()

        // ── 1. Background location ────────────────────────────────────────────
        val bgLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        if (bgLocation != PackageManager.PERMISSION_GRANTED) {
            issues += PermissionDiagnostic.BackgroundLocationMissing
        }

        // ── 2. Battery optimisation exemption ─────────────────────────────────
        // isIgnoringBatteryOptimizations is only meaningful on API 23+.
        // On lower API levels the concept of battery optimisation does not
        // exist, so the check is skipped to avoid a false-positive warning.
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            issues += PermissionDiagnostic.BatteryOptimizationActive
        }

        // ── 3. Notification permission (Android 13+) ──────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (notifPermission != PackageManager.PERMISSION_GRANTED) {
                issues += PermissionDiagnostic.NotificationPermissionMissing
            }
        }

        val transitionRegistrar = ActivityTransitionRegistrar(context)
        if (transitionRegistrar.isEnabled()) {
            transitionRegistrar.registrationFailure()?.let { reason ->
                issues += PermissionDiagnostic.ActivityTransitionRegistrationFailed(reason)
            }
        }

        return if (issues.isEmpty()) listOf(PermissionDiagnostic.AllClear) else issues
    }
}
