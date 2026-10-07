package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.repository.TrackingRepositoryImpl
import com.example.mileagetracker.domain.model.RoutePoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrackingRepositoryImplTest {

    private lateinit var dao: RoutePointDao
    private lateinit var repository: TrackingRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk()
        repository = TrackingRepositoryImpl(dao)
    }

    private fun testPoint(id: Long = 0, tripId: Long = 1) = RoutePoint(
        id = id,
        tripId = tripId,
        lat = 1.0,
        lng = 2.0,
        altitudeMetres = 10.0,
        accuracyMetres = 5.0f,
        speedKph = 50.0f,
        timestampMs = 1000L
    )

    @Test
    fun `saveRoutePoints maps to entities and delegates to dao`() = runTest {
        val points = listOf(testPoint())
        coEvery { dao.insertAll(any()) } returns Unit

        repository.saveRoutePoints(1L, points)

        coVerify(exactly = 1) { dao.insertAll(any()) }
    }

    @Test
    fun `getRoutePoints maps dao entities to domain`() = runTest {
        val entity = RoutePointEntity(
            id = 1,
            tripId = 1L,
            lat = 1.0,
            lng = 2.0,
            altitudeMetres = 10.0,
            accuracyMetres = 5.0f,
            speedKph = 50.0f,
            timestampMs = 1000L
        )
        every { dao.getPointsForTrip(1L) } returns flowOf(listOf(entity))

        val result = repository.getRoutePoints(1L).first()

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }
}
