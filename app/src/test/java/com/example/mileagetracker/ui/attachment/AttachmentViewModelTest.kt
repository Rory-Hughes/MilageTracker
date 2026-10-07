package com.example.mileagetracker.ui.attachment

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.usecase.attachment.AddAttachmentUseCase
import com.example.mileagetracker.domain.usecase.attachment.DeleteAttachmentUseCase
import com.example.mileagetracker.domain.usecase.attachment.GetAttachmentsForTripUseCase
import com.example.mileagetracker.navigation.Screen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentViewModelTest {

    private lateinit var viewModel: AttachmentViewModel
    private lateinit var getAttachmentsForTripUseCase: GetAttachmentsForTripUseCase
    private lateinit var addAttachmentUseCase: AddAttachmentUseCase
    private lateinit var deleteAttachmentUseCase: DeleteAttachmentUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getAttachmentsForTripUseCase = mockk()
        addAttachmentUseCase = mockk()
        deleteAttachmentUseCase = mockk()

        every { getAttachmentsForTripUseCase(1) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun initViewModel(tripId: Int? = 1) {
        val savedStateHandle = SavedStateHandle().apply {
            if (tripId != null) set(Screen.ARG_DRIVE_ID, tripId)
        }
        viewModel = AttachmentViewModel(
            savedStateHandle,
            getAttachmentsForTripUseCase,
            addAttachmentUseCase,
            deleteAttachmentUseCase
        )
    }

    @Test
    fun `initial state loads attachments`() = runTest {
        val attachment = TripAttachment(1, 1, "uri", "image/png", "file.png", 1000L)
        every { getAttachmentsForTripUseCase(1) } returns flowOf(listOf(attachment))

        initViewModel(1)
        val collector = backgroundScope.launch { viewModel.attachments.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(attachment), viewModel.attachments.value)
        collector.cancel()
    }

    @Test
    fun `addAttachment triggers AddSuccess event`() = runTest {
        initViewModel(1)
        val uri = mockk<Uri>()
        val attachment = TripAttachment(1, 1, "uri", "image/png", "file.png", 1000L)
        coEvery { addAttachmentUseCase(1, uri, "image/png", "test.png") } returns attachment

        viewModel.addAttachment(uri, "image/png", "test.png")
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is AttachmentEvent.AddSuccess)
        assertEquals(attachment, (event as AttachmentEvent.AddSuccess).attachment)
    }

    @Test
    fun `deleteAttachment triggers DeleteSuccess event`() = runTest {
        initViewModel(1)
        val attachment = TripAttachment(1, 1, "uri", "image/png", "file.png", 1000L)
        coEvery { deleteAttachmentUseCase(attachment) } returns Unit

        viewModel.deleteAttachment(attachment)
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(AttachmentEvent.DeleteSuccess, event)
    }
}
