package com.example.mileagetracker.service

import com.example.mileagetracker.domain.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDistanceCalculatorTest {

    private fun testPoint(lat: Double, lng: Double) = RoutePoint(
        id = 0,
        tripId = 1,
        lat = lat,
        lng = lng,
        altitudeMetres = null,
        accuracyMetres = null,
        speedKph = null,
        timestampMs = 0
    )

    @Test
    fun `calculates zero distance for empty or single point list`() {
        assertEquals(0f, RouteDistanceCalculator.calculateTotalDistanceKm(emptyList()))
        assertEquals(0f, RouteDistanceCalculator.calculateTotalDistanceKm(listOf(testPoint(0.0, 0.0))))
    }

    @Test
    fun `calculates distance between two points correctly`() {
        // Toronto (43.6532, -79.3832) to Montreal (45.5017, -73.5673) is ~500km
        val toronto = testPoint(43.6532, -79.3832)
        val montreal = testPoint(45.5017, -73.5673)
        
        val distance = RouteDistanceCalculator.calculateTotalDistanceKm(listOf(toronto, montreal))
        
        // Expected is approx 504km. We check within a reasonable delta for Haversine.
        assertEquals(504.0f, distance, 1.0f)
    }

    @Test
    fun `calculates cumulative distance for multiple points`() {
        val p1 = testPoint(0.0, 0.0)
        val p2 = testPoint(0.0, 0.1) // ~11.1 km at equator
        val p3 = testPoint(0.0, 0.2)
        
        val distance = RouteDistanceCalculator.calculateTotalDistanceKm(listOf(p1, p2, p3))
        
        assertEquals(22.2f, distance, 0.1f)
    }
}
