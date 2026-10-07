package com.example.mileagetracker

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mileagetracker.navigation.Screen
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onboardingIncomplete_startsOnOnboarding() {
        var onboardingComplete by mutableStateOf(false)
        var isLoggedIn by mutableStateOf(false)

        composeRule.setContent {
            val navController = rememberNavController()
            LaunchedEffect(onboardingComplete, isLoggedIn) {
                val target = resolveStartDestination(onboardingComplete, isLoggedIn)
                if (navController.currentDestination?.route != target) {
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = resolveStartDestination(onboardingComplete, isLoggedIn),
            ) {
                composable(Screen.Onboarding.route) { Text("Onboarding") }
                composable(Screen.Login.route) { Text("Login") }
                composable(Screen.Main.route) { Text("Main") }
            }
        }

        composeRule.onNodeWithText("Onboarding").assertIsDisplayed()
    }

    @Test
    fun completingOnboarding_movesToLoginWithoutRestart() {
        var onboardingComplete by mutableStateOf(false)
        var isLoggedIn by mutableStateOf(false)

        composeRule.setContent {
            val navController = rememberNavController()
            LaunchedEffect(onboardingComplete, isLoggedIn) {
                val target = resolveStartDestination(onboardingComplete, isLoggedIn)
                if (navController.currentDestination?.route != target) {
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = resolveStartDestination(onboardingComplete, isLoggedIn),
            ) {
                composable(Screen.Onboarding.route) { Text("Onboarding") }
                composable(Screen.Login.route) { Text("Login") }
                composable(Screen.Main.route) { Text("Main") }
            }
        }

        composeRule.onNodeWithText("Onboarding").assertIsDisplayed()
        composeRule.runOnUiThread { onboardingComplete = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun completedOnboardingAndLogin_showsMain() {
        var onboardingComplete by mutableStateOf(true)
        var isLoggedIn by mutableStateOf(false)

        composeRule.setContent {
            val navController = rememberNavController()
            LaunchedEffect(onboardingComplete, isLoggedIn) {
                val target = resolveStartDestination(onboardingComplete, isLoggedIn)
                if (navController.currentDestination?.route != target) {
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = resolveStartDestination(onboardingComplete, isLoggedIn),
            ) {
                composable(Screen.Onboarding.route) { Text("Onboarding") }
                composable(Screen.Login.route) { Text("Login") }
                composable(Screen.Main.route) { Text("Main") }
            }
        }

        composeRule.onNodeWithText("Login").assertIsDisplayed()
        composeRule.runOnUiThread { isLoggedIn = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Main").assertIsDisplayed()
    }
}
