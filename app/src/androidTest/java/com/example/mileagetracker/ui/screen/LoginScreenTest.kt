package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.ui.viewmodel.AuthViewModel
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val authViewModel: AuthViewModel = mockk(relaxed = true)

    private fun setContent(onLoginSuccess: () -> Unit = {}) {
        composeRule.setContent {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = onLoginSuccess,
            )
        }
    }

    @Test
    fun loginScreen_showsAppTitle() {
        setContent()

        composeRule.onNodeWithText("MileageTracker").assertIsDisplayed()
    }

    @Test
    fun loginScreen_showsWelcomeMessage() {
        setContent()

        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun loginButton_disabledWhenFieldsEmpty() {
        setContent()

        composeRule.onNodeWithText("Log In").assertIsNotEnabled()
    }

    @Test
    fun loginButton_enabledWhenFieldsFilled() {
        setContent()

        composeRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeRule.onNodeWithText("Password").performTextInput("password123")

        composeRule.onNodeWithText("Log In").assertIsEnabled()
    }

    @Test
    fun switchToSignUp_changesHeaderAndButtonText() {
        setContent()

        composeRule.onNodeWithText("Don't have an account? Sign Up").performClick()

        composeRule.onNodeWithText("Create your account").assertIsDisplayed()
        composeRule.onNodeWithText("Create Account").assertIsDisplayed()
    }

    @Test
    fun switchToSignUp_hidesForgotPasswordButton() {
        setContent()

        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
        composeRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        composeRule.onNodeWithText("Forgot password?").assertDoesNotExist()
    }

    @Test
    fun switchBackToLogin_restoresLoginState() {
        setContent()

        composeRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        composeRule.onNodeWithText("Already have an account? Log In").performClick()

        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Log In").assertIsDisplayed()
    }

    @Test
    fun forgotPassword_disabledWhenEmailEmpty() {
        setContent()

        composeRule.onNodeWithText("Forgot password?").assertIsNotEnabled()
    }

    @Test
    fun forgotPassword_enabledWhenEmailFilled() {
        setContent()

        composeRule.onNodeWithText("Email").performTextInput("test@example.com")

        composeRule.onNodeWithText("Forgot password?").assertIsEnabled()
    }
}
