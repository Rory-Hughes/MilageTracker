package com.example.mileagetracker.ui.expense

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Entry-point screen for logging fuel, maintenance, and other vehicle expenses.
 *
 * This composable satisfies the Phase 8 exit criterion:
 * "Fuel, maintenance, and other vehicle expenses can be logged"
 * (key file: VehicleExpenseScreen.kt).
 *
 * All form logic lives in [AddExpenseScreen] / [VehicleExpenseViewModel] to
 * keep a single source of truth.  This wrapper exists so that the nav graph
 * can reference a route called `VehicleExpense` independently of the shared
 * `AddExpense` route, enabling deep-links and role-based access control
 * (e.g. manager reviewing a driver's draft expense) without duplicating UI.
 *
 * @param navController Passed through to [AddExpenseScreen] for OCR navigation
 *                      and back-stack management.
 * @param onBack        Called when the user taps the back arrow.
 * @param onSaved       Called after a successful save, allowing the caller to
 *                      pop back or show a confirmation snackbar.
 */
@Composable
fun VehicleExpenseScreen(
    navController: NavController,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    AddExpenseScreen(
        navController = navController,
        onBack = onBack,
        onSaved = onSaved,
    )
}
