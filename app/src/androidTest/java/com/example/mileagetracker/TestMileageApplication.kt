package com.example.mileagetracker

import dagger.hilt.android.testing.CustomTestApplication

// Tells Hilt to generate a test application that extends THIS base class.
// This skips all the real MileageApplication.onCreate() side effects.
@CustomTestApplication(BaseTestApplication::class)
interface HiltTestApplication

open class BaseTestApplication : android.app.Application() {
    // Intentionally empty — no Firebase, no Places, no WorkManager scheduling.
    // Room DAO tests need none of that.
}
