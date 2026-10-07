package com.example.mileagetracker.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mileagetracker.ui.expense.OcrReceiptScreen
import com.example.mileagetracker.ui.expense.VehicleExpenseScreen

fun NavGraphBuilder.addExpenseDestinations(navController: NavHostController) {
    addVehicleExpenseDestination(navController = navController)
    addOcrReceiptDestination(navController = navController)
}

private fun NavGraphBuilder.addVehicleExpenseDestination(navController: NavHostController) {
    composable(
        route = Screen.VehicleExpense.route,
        arguments = listOf(
            navArgument(Screen.ARG_VEHICLE_ID) { type = NavType.IntType },
            navArgument(Screen.ARG_YEAR) {
                type = NavType.IntType
                defaultValue = java.time.LocalDate.now().year
            },
        ),
    ) {
        VehicleExpenseScreen(
            navController = navController,
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.addOcrReceiptDestination(navController: NavHostController) {
    composable(Screen.OcrReceipt.route) {
        OcrReceiptScreen(
            onBack = { navController.popBackStack() },
            onConfirm = { amountRaw, vendor, dateMillis, gstHstRaw ->
                val previousState = navController.previousBackStackEntry?.savedStateHandle
                previousState?.set(Screen.OCR_AMOUNT, amountRaw)
                previousState?.set(Screen.OCR_VENDOR, vendor)
                previousState?.set(Screen.OCR_DATE, dateMillis)
                previousState?.set(Screen.OCR_GST, gstHstRaw)
                navController.popBackStack()
            },
        )
    }
}
