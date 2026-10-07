package com.example.mileagetracker.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTests {

    @Test
    fun `RoutePointEntity toDomain maps all fields`() {
        val entity = RoutePointEntity(
            id = 1L,
            tripId = 10L,
            lat = 45.0,
            lng = -63.0,
            altitudeMetres = 100.0,
            accuracyMetres = 5.0f,
            speedKph = 50.0f,
            timestampMs = 1000L
        )
        
        val domain = entity.toDomain()
        
        assertEquals(entity.id, domain.id)
        assertEquals(entity.tripId, domain.tripId)
        assertEquals(entity.lat, domain.lat, 0.0)
        assertEquals(entity.lng, domain.lng, 0.0)
        assertEquals(entity.altitudeMetres, domain.altitudeMetres)
        assertEquals(entity.accuracyMetres, domain.accuracyMetres)
        assertEquals(entity.speedKph, domain.speedKph)
        assertEquals(entity.timestampMs, domain.timestampMs)
    }
}
