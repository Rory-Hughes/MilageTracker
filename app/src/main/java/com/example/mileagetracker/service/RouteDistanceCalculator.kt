package com.example.mileagetracker.service

import com.example.mileagetracker.domain.model.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object RouteDistanceCalculator {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun calculateTotalDistanceKm(points: List<RoutePoint>): Float {
        if (points.size < 2) return 0f

        var totalMeters = 0.0
        for (i in 1 until points.size) {
            totalMeters += haversineMeters(
                lat1 = points[i - 1].lat,
                lon1 = points[i - 1].lng,
                lat2 = points[i].lat,
                lon2 = points[i].lng,
            )
        }
        return (totalMeters / 1_000.0).toFloat()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(originLat) * cos(destinationLat) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
