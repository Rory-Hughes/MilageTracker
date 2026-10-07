package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.AuthViewModel

private data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isSignUpMode: Boolean = false,
)

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
) {
    var state by remember { mutableStateOf(LoginUiState()) }
    LoginScreenContent(
        state = state,
        onEmailChanged = { state = state.copy(email = it) },
        onPasswordChanged = { state = state.copy(password = it) },
        onPasswordVisibilityToggled = {
            state = state.copy(isPasswordVisible = !state.isPasswordVisible)
        },
        onSubmit = {
            state = submitAuth(
                state = state,
                viewModel = viewModel,
                onStateChanged = { state = it },
                onLoginSuccess = onLoginSuccess,
            )
        },
        onModeToggled = {
            state = state.copy(
                isSignUpMode = !state.isSignUpMode,
                errorMessage = null,
                infoMessage = null,
            )
        },
        onForgotPassword = {
            state = submitPasswordReset(
                state = state,
                viewModel = viewModel,
                onStateChanged = { state = it },
            )
        },
    )
}

@Composable
private fun LoginScreenContent(
    state: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityToggled: () -> Unit,
    onSubmit: () -> Unit,
    onModeToggled: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val isSubmitEnabled = !state.isLoading && state.email.isNotBlank() && state.password.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoginHeader(isSignUpMode = state.isSignUpMode)
        Spacer(modifier = Modifier.height(Spacing.xxl))
        LoginFormFields(
            state = state,
            onEmailChanged = onEmailChanged,
            onPasswordChanged = onPasswordChanged,
            onPasswordVisibilityToggled = onPasswordVisibilityToggled,
        )
        StatusMessages(
            errorMessage = state.errorMessage,
            infoMessage = state.infoMessage,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))
        AuthActionButton(
            isLoading = state.isLoading,
            isEnabled = isSubmitEnabled,
            isSignUpMode = state.isSignUpMode,
            onClick = onSubmit,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        LoginModeToggle(
            isSignUpMode = state.isSignUpMode,
            onToggle = onModeToggled,
        )
        if (!state.isSignUpMode) {
            ForgotPasswordAction(
                email = state.email,
                isLoading = state.isLoading,
                onClick = onForgotPassword,
            )
        }
    }
}

private fun submitAuth(
    state: LoginUiState,
    viewModel: AuthViewModel,
    onStateChanged: (LoginUiState) -> Unit,
    onLoginSuccess: () -> Unit,
): LoginUiState {
    val loadingState = state.copy(isLoading = true, errorMessage = null, infoMessage = null)
    if (state.isSignUpMode) {
        submitSignUp(
            viewModel = viewModel,
            email = state.email,
            password = state.password,
            currentState = loadingState,
            onStateChanged = onStateChanged,
        )
    } else {
        submitLogin(
            viewModel = viewModel,
            email = state.email,
            password = state.password,
            currentState = loadingState,
            onStateChanged = onStateChanged,
            onLoginSuccess = onLoginSuccess,
        )
    }
    return loadingState
}

private fun submitPasswordReset(
    state: LoginUiState,
    viewModel: AuthViewModel,
    onStateChanged: (LoginUiState) -> Unit,
): LoginUiState {
    val loadingState = state.copy(isLoading = true, errorMessage = null, infoMessage = null)
    viewModel.resetPassword(
        email = state.email,
        onSuccess = {
            onStateChanged(
                loadingState.copy(
                    isLoading = false,
                    infoMessage = "Password reset email sent.",
                ),
            )
        },
        onError = { error ->
            onStateChanged(
                loadingState.copy(
                    isLoading = false,
                    errorMessage = error,
                ),
            )
        },
    )
    return loadingState
}

private fun submitSignUp(
    viewModel: AuthViewModel,
    email: String,
    password: String,
    currentState: LoginUiState,
    onStateChanged: (LoginUiState) -> Unit,
) {
    viewModel.signUp(
        email = email,
        pass = password,
        onPendingVerification = { message ->
            onStateChanged(
                currentState.copy(
                    isLoading = false,
                    infoMessage = message,
                    errorMessage = null,
                ),
            )
        },
        onError = { error ->
            onStateChanged(
                currentState.copy(
                    isLoading = false,
                    errorMessage = error,
                    infoMessage = null,
                ),
            )
        },
    )
}

private fun submitLogin(
    viewModel: AuthViewModel,
    email: String,
    password: String,
    currentState: LoginUiState,
    onStateChanged: (LoginUiState) -> Unit,
    onLoginSuccess: () -> Unit,
) {
    viewModel.login(
        email = email,
        pass = password,
        onSuccess = {
            onStateChanged(currentState.copy(isLoading = false))
            onLoginSuccess()
        },
        onError = { error ->
            onStateChanged(
                currentState.copy(
                    isLoading = false,
                    errorMessage = error,
                    infoMessage = null,
                ),
            )
        },
    )
}

@Composable
private fun LoginHeader(isSignUpMode: Boolean) {
    Text(
        text = "MileageTracker",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
    Text(
        text = if (isSignUpMode) "Create your account" else "Welcome back",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoginFormFields(
    state: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityToggled: () -> Unit,
) {
    OutlinedTextField(
        value = state.email,
        onValueChange = onEmailChanged,
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChanged,
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (state.isPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onPasswordVisibilityToggled) {
                Icon(
                    imageVector = if (state.isPasswordVisible) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = if (state.isPasswordVisible) {
                        "Hide password"
                    } else {
                        "Show password"
                    },
                )
            }
        },
    )
}

@Composable
private fun AuthActionButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    isSignUpMode: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = isEnabled,
        onClick = onClick,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(if (isSignUpMode) "Create Account" else "Log In")
        }
    }
}

@Composable
private fun LoginModeToggle(
    isSignUpMode: Boolean,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle) {
        Text(
            if (isSignUpMode) {
                "Already have an account? Log In"
            } else {
                "Don't have an account? Sign Up"
            },
        )
    }
}

@Composable
private fun ForgotPasswordAction(
    email: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = email.isNotBlank() && !isLoading,
        onClick = onClick,
    ) {
        Text("Forgot password?")
    }
}
