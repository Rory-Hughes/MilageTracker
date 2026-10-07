package com.example.mileagetracker.service

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveLocationBusTest {

    @Test
    fun `initial location is null`() {
        val bus = LiveLocationBus()
        assertNull(bus.location.value)
    }

    @Test
    fun `emit updates location`() {
        val bus = LiveLocationBus()
        val pos = LatLng(43.6, -79.3)
        bus.emit(pos)
        assertEquals(pos, bus.location.value)
    }

    @Test
    fun `clear resets location to null`() {
        val bus = LiveLocationBus()
        bus.emit(LatLng(0.0, 0.0))
        bus.clear()
        assertNull(bus.location.value)
    }
}
