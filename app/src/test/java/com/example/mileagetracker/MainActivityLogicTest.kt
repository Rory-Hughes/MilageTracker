package com.example.mileagetracker

import com.example.mileagetracker.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLogicTest {

    @Test
    fun `resolveStartDestination returns Onboarding when not complete`() {
        val result = resolveStartDestination(onboardingComplete = false, isLoggedIn = true)
        assertEquals(Screen.Onboarding.route, result)
    }

    @Test
    fun `resolveStartDestination returns Login when onboarding complete but not logged in`() {
        val result = resolveStartDestination(onboardingComplete = true, isLoggedIn = false)
        assertEquals(Screen.Login.route, result)
    }

    @Test
    fun `resolveStartDestination returns Main when both complete`() {
        val result = resolveStartDestination(onboardingComplete = true, isLoggedIn = true)
        assertEquals(Screen.Main.route, result)
    }
}
