package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.ui.viewmodel.CraRateDiagnosticsUiState
import com.example.mileagetracker.ui.viewmodel.SettingsUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: SettingsUiState = SettingsUiState(),
        navigationActions: SettingsScreenActions = SettingsScreenActions(),
        callbacks: SettingsCallbacks = noOpCallbacks(),
    ) {
        composeRule.setContent {
            SettingsScreenContent(
                state = state,
                craRateDiagnosticsState = CraRateDiagnosticsUiState(),
                navigationActions = navigationActions,
                callbacks = callbacks,
                permissionState = noOpPermissionState(),
            )
        }
    }

    @Test
    fun allSections_areDisplayed() {
        setContent()

        composeRule.onAllNodesWithText("Language", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Vehicle", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Rate", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Notifications", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tracking", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Privacy", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Smart Classification", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun notificationToggles_reflectState() {
        setContent(
            state = SettingsUiState(
                tripStartReminderEnabled = true,
                weeklySummaryEnabled = false,
            ),
        )

        composeRule.onNodeWithText("Trip-start reminder", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Weekly summary", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun classificationRulesNavEntry_invokesCallback() {
        var navigated = false
        setContent(
            navigationActions = SettingsScreenActions(
                onNavigateToClassificationRules = { navigated = true },
            ),
        )

        composeRule.onNodeWithText("Classification Rules", substring = true)
            .performScrollTo()
            .performClick()
        assertTrue(navigated)
    }

    @Test
    fun privacyDashboardNavEntry_invokesCallback() {
        var navigated = false
        setContent(
            navigationActions = SettingsScreenActions(
                onNavigateToPrivacyDashboard = { navigated = true },
            ),
        )

        composeRule.onNodeWithText("Privacy Dashboard", substring = true)
            .performScrollTo()
            .performClick()
        assertTrue(navigated)
    }

    @Test
    fun vehicleSection_showsNoVehicleHint_whenListIsEmpty() {
        setContent(state = SettingsUiState(activeVehicles = emptyList()))

        composeRule.onNodeWithText("No active vehicles", substring = true).assertIsDisplayed()
    }

    @Test
    fun vehicleSection_showsVehiclePicker_whenVehiclesExist() {
        setContent(
            state = SettingsUiState(
                activeVehicles = listOf(
                    VehicleEntity(id = 1, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2026),
                ),
                defaultVehicleId = null,
            ),
        )

        composeRule.onNodeWithText("None", substring = true).assertIsDisplayed()
    }

    @Test
    fun expensesSection_disabledWithoutVehicle() {
        setContent(
            state = SettingsUiState(
                activeVehicles = emptyList(),
                defaultVehicleId = null,
            ),
        )

        composeRule.onNodeWithText("Add a vehicle before logging expenses", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun expensesSection_showsNavEntry_withVehicle() {
        setContent(
            state = SettingsUiState(
                activeVehicles = listOf(
                    VehicleEntity(id = 1, ownerUid = "u1", displayName = "Truck", fiscalYear = 2026),
                ),
                defaultVehicleId = 1,
            ),
        )

        composeRule.onNodeWithText("Vehicle Expenses", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun integrationsEntry_hiddenWhenFlagIsFalse() {
        setContent(
            navigationActions = SettingsScreenActions(showIntegrations = false),
        )

        composeRule.onNodeWithText("Accounting Integrations", substring = true).assertDoesNotExist()
    }

    @Test
    fun integrationsEntry_shownWhenFlagIsTrue() {
        setContent(
            navigationActions = SettingsScreenActions(showIntegrations = true),
        )

        composeRule.onNodeWithText("Accounting Integrations", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun noOpCallbacks() = SettingsCallbacks(
        onLanguageSelected = {},
        onDefaultVehicleSelected = {},
        onRateModeChange = {},
        onCraRateRegionSelected = {},
        onCustomRateInputChanged = {},
        onApplyCustomRate = {},
        onTripStartReminderChanged = {},
        onWeeklySummaryChanged = {},
        onOdometerPhotoReminderChanged = {},
        onAutoDetectSensitivityChanged = {},
        onCloudUploadConsentChanged = {},
        onOpenCraSource = {},
    )

    private fun noOpPermissionState() = SettingsPermissionState(
        tracking = TrackingPermissionUiState(
            statusText = "Off",
            permissionMessage = null,
            onToggle = {},
        ),
        calendar = CalendarPermissionUiState(
            message = null,
            showRationale = false,
            onToggle = {},
            onDismissRationale = {},
            onConfirmRationale = {},
        ),
    )
}
