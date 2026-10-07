package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetUnclassifiedDrivesUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var useCase: GetUnclassifiedDrivesUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        useCase = GetUnclassifiedDrivesUseCase(driveRepository)
    }

    @Test
    fun `delegates to repository`() {
        val expected = flowOf(emptyList<DriveEntity>())
        every { driveRepository.getUnclassifiedDrives("u1") } returns expected

        val result = useCase("u1")

        assertEquals(expected, result)
    }
}
