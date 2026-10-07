package com.example.mileagetracker.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FirebaseTripEndpointAddressRepositoryTest {

    private lateinit var functions: FirebaseFunctions
    private lateinit var callable: HttpsCallableReference
    private lateinit var repository: FirebaseTripEndpointAddressRepository

    @Before
    fun setUp() {
        functions = mockk()
        callable = mockk()
        repository = FirebaseTripEndpointAddressRepository(functions)

        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { functions.getHttpsCallable("reverseGeocodeTripEndpoints") } returns callable
    }

    @After
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `reverseGeocodeTripEndpoints sends callable payload and maps addresses`() = runTest {
        val payloadSlot = slot<Map<String, Map<String, Double>>>()
        val task = mockk<Task<HttpsCallableResult>>()
        val result = callableResult(
            mapOf(
                "startAddress" to "1800 Lower Water St, Halifax, NS B3J 3R8",
                "endAddress" to "6960 Mumford Rd, Halifax, NS B3L 4P1",
            ),
        )
        every { callable.call(capture(payloadSlot)) } returns task
        coEvery { task.await() } returns result

        val addresses = repository.reverseGeocodeTripEndpoints(
            startLat = 44.6488,
            startLng = -63.5752,
            endLat = 44.6713,
            endLng = -63.6103,
        )

        val payload = payloadSlot.captured
        assertEquals(44.6488, payload.getValue("start").getValue("latitude"), 0.0)
        assertEquals(-63.5752, payload.getValue("start").getValue("longitude"), 0.0)
        assertEquals(44.6713, payload.getValue("end").getValue("latitude"), 0.0)
        assertEquals(-63.6103, payload.getValue("end").getValue("longitude"), 0.0)
        assertEquals("1800 Lower Water St, Halifax, NS B3J 3R8", addresses.startAddress)
        assertEquals("6960 Mumford Rd, Halifax, NS B3L 4P1", addresses.endAddress)
    }

    @Test
    fun `reverseGeocodeTripEndpoints returns null addresses when callable payload is malformed`() = runTest {
        val task = mockk<Task<HttpsCallableResult>>()
        val result = callableResult("unexpected")
        every { callable.call(any()) } returns task
        coEvery { task.await() } returns result

        val addresses = repository.reverseGeocodeTripEndpoints(
            startLat = 44.6488,
            startLng = -63.5752,
            endLat = 44.6713,
            endLng = -63.6103,
        )

        assertNull(addresses.startAddress)
        assertNull(addresses.endAddress)
    }

    private fun callableResult(data: Any?): HttpsCallableResult {
        val constructor = HttpsCallableResult::class.java.getDeclaredConstructor(Any::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(data)
    }
}
