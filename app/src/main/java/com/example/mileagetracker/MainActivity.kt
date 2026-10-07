package com.example.mileagetracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mileagetracker.navigation.Screen
import com.example.mileagetracker.ui.screen.LoginScreen
import com.example.mileagetracker.ui.screen.MainAppShell
import com.example.mileagetracker.ui.screen.OnboardingScreen
import com.example.mileagetracker.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

// This is the main activity of the Mileage Tracker app.
// It sets up the navigation and handles the onboarding and login flow.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MileageApp() }
    }
}

// The MileageApp composable sets up the navigation and handles the onboarding and login flow.
// It uses the AuthViewModel to determine if the user is logged in and checks the shared preferences
// to determine whether they have completed onboarding.
// Based on this information, it navigates to the appropriate screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageApp() {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val navController = rememberNavController()
    var onboardingComplete by remember { mutableStateOf(hasCompletedOnboarding(context)) }

    // Clean, clear 3-way logic
    val startDestination = resolveStartDestination(
        onboardingComplete = onboardingComplete,
        isLoggedIn = isLoggedIn,
    )

    // Set up the navigation host with the appropriate start destination and composable screens
    NavHost(navController = navController, startDestination = startDestination) {
        // Onboarding screen is shown if the user has not completed onboarding.
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    markOnboardingComplete(context)
                    onboardingComplete = true
                    val target = if (isLoggedIn) Screen.Main.route else Screen.Login.route
                    navController.navigate(target) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        // Login screen is shown if the user is not logged in.
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        // Main app screen is shown if the user is logged in.
        composable(Screen.Main.route) {
            MainAppShell(
                authViewModel = authViewModel,
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

// Shared preferences keys for onboarding state
private const val ONBOARDING_PREFS = "onboarding"
private const val KEY_ONBOARDING_COMPLETE = "complete"

// Check if the user has completed onboarding by reading from shared preferences
private fun hasCompletedOnboarding(context: Context): Boolean =
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDING_COMPLETE, false)

// Mark onboarding as complete by writing to shared preferences
private fun markOnboardingComplete(context: Context) {
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ONBOARDING_COMPLETE, true)
        .apply()
}

// Resolve the start destination based on onboarding and login state
internal fun resolveStartDestination(
    onboardingComplete: Boolean,
    isLoggedIn: Boolean,
): String {
    return when {
        !onboardingComplete -> Screen.Onboarding.route
        !isLoggedIn -> Screen.Login.route
        else -> Screen.Main.route
    }
}
