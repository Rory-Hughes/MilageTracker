package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.service.LiveLocationBus
import com.example.mileagetracker.service.TrackingCommandDispatcher
import com.example.mileagetracker.service.TrackingStateBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ManualTripControlViewModelTest {

    private lateinit var dispatcher: TrackingCommandDispatcher
    private lateinit var stateBus: TrackingStateBus
    private lateinit var locationBus: LiveLocationBus
    private lateinit var viewModel: ManualTripControlViewModel

    @Before
    fun setUp() {
        dispatcher = mockk(relaxed = true)
        stateBus = mockk(relaxed = true)
        locationBus = mockk(relaxed = true)
        viewModel = ManualTripControlViewModel(dispatcher, stateBus, locationBus)
    }

    @Test
    fun `startManualTrip delegates to dispatcher`() {
        viewModel.startManualTrip()
        verify { dispatcher.startManual() }
    }

    @Test
    fun `stopManualTrip delegates to dispatcher`() {
        viewModel.stopManualTrip()
        verify { dispatcher.stopManual() }
    }

    @Test
    fun `trackingState is sourced from stateBus`() {
        val mockState = com.example.mileagetracker.domain.model.TrackingState.Idle
        every { stateBus.state } returns kotlinx.coroutines.flow.MutableStateFlow(mockState)
        
        val viewModelWithMock = ManualTripControlViewModel(dispatcher, stateBus, locationBus)
        assertEquals(mockState, viewModelWithMock.trackingState.value)
    }

    @Test
    fun `liveLocation is sourced from locationBus`() {
        val mockLocation = com.google.android.gms.maps.model.LatLng(45.0, -63.0)
        every { locationBus.location } returns kotlinx.coroutines.flow.MutableStateFlow(mockLocation)
        
        val viewModelWithMock = ManualTripControlViewModel(dispatcher, stateBus, locationBus)
        assertEquals(mockLocation, viewModelWithMock.liveLocation.value)
    }
}
