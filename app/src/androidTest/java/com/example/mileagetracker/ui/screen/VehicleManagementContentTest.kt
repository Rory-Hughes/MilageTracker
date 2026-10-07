package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.VehicleEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleManagementContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsAddPrompt() {
        var addClicked = false
        composeRule.setContent {
            VehicleManagementContent(
                vehicles = emptyList(),
                padding = PaddingValues(0.dp),
                onAddVehicle = { addClicked = true },
                onEditVehicle = {},
                onSetDefault = {},
                onDeactivate = {},
                onReactivate = {},
                onDelete = {},
                onRecordOdometer = {},
            )
        }

        composeRule.onAllNodesWithText("Add", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun vehicleList_displaysVehicleNames() {
        val vehicles = listOf(
            testVehicle(id = 1, name = "My Civic"),
            testVehicle(id = 2, name = "Work Van"),
        )
        composeRule.setContent {
            VehicleManagementContent(
                vehicles = vehicles,
                padding = PaddingValues(0.dp),
                onAddVehicle = {},
                onEditVehicle = {},
                onSetDefault = {},
                onDeactivate = {},
                onReactivate = {},
                onDelete = {},
                onRecordOdometer = {},
            )
        }

        composeRule.onNodeWithText("My Civic").assertIsDisplayed()
        composeRule.onNodeWithText("Work Van").assertIsDisplayed()
    }

    @Test
    fun defaultVehicle_showsDefaultBadge() {
        val vehicles = listOf(
            testVehicle(id = 1, name = "Default Car", isDefault = true),
        )
        composeRule.setContent {
            VehicleManagementContent(
                vehicles = vehicles,
                padding = PaddingValues(0.dp),
                onAddVehicle = {},
                onEditVehicle = {},
                onSetDefault = {},
                onDeactivate = {},
                onReactivate = {},
                onDelete = {},
                onRecordOdometer = {},
            )
        }

        composeRule.onNodeWithText("Default Car").assertIsDisplayed()
        composeRule.onAllNodesWithText("Default", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun inactiveVehicle_stillDisplayed() {
        val vehicles = listOf(
            testVehicle(id = 1, name = "Retired Truck", isActive = false),
        )
        composeRule.setContent {
            VehicleManagementContent(
                vehicles = vehicles,
                padding = PaddingValues(0.dp),
                onAddVehicle = {},
                onEditVehicle = {},
                onSetDefault = {},
                onDeactivate = {},
                onReactivate = {},
                onDelete = {},
                onRecordOdometer = {},
            )
        }

        composeRule.onNodeWithText("Retired Truck").assertIsDisplayed()
    }

    @Test
    fun vehicleCard_showsMakeModelYear() {
        val vehicles = listOf(
            testVehicle(id = 1, name = "Daily", make = "Honda", model = "Civic", year = 2022),
        )
        composeRule.setContent {
            VehicleManagementContent(
                vehicles = vehicles,
                padding = PaddingValues(0.dp),
                onAddVehicle = {},
                onEditVehicle = {},
                onSetDefault = {},
                onDeactivate = {},
                onReactivate = {},
                onDelete = {},
                onRecordOdometer = {},
            )
        }

        composeRule.onNodeWithText("Honda", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Civic", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2022", substring = true).assertIsDisplayed()
    }

    private fun testVehicle(
        id: Int = 1,
        name: String = "Vehicle",
        make: String? = null,
        model: String? = null,
        year: Int? = null,
        isDefault: Boolean = false,
        isActive: Boolean = true,
    ) = VehicleEntity(
        id = id,
        ownerUid = "test-uid",
        displayName = name,
        make = make,
        model = model,
        year = year,
        fiscalYear = 2026,
        isDefault = isDefault,
        isActive = isActive,
    )
}
