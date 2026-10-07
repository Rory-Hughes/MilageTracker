package com.example.mileagetracker.service

import com.example.mileagetracker.domain.model.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingStateBusTest {

    @Test
    fun `initial state is Idle`() {
        val bus = TrackingStateBus()
        assertEquals(TrackingState.Idle, bus.state.value)
    }

    @Test
    fun `emit updates state`() {
        val bus = TrackingStateBus()
        val newState = TrackingState.ManualRecording(1000L, 10.5f)
        bus.emit(newState)
        assertEquals(newState, bus.state.value)
    }
}
