package com.example.mileagetracker.domain.usecase.account

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LegacyClaimUseCaseTest {

    private lateinit var database: MileageDatabase
    private lateinit var accountDataDao: AccountDataDao
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: LegacyClaimUseCase

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        database = mockk()
        accountDataDao = mockk()
        currentUserProvider = mockk()
        useCase = LegacyClaimUseCase(database, accountDataDao, currentUserProvider)

        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `preview returns correct counts`() = runTest {
        coEvery { accountDataDao.countLegacyDrives() } returns 10
        coEvery { accountDataDao.countLegacyVehicles() } returns 2
        coEvery { accountDataDao.countLegacyClassificationRules() } returns 3

        val result = useCase.preview()

        assertEquals(10, result.driveCount)
        assertEquals(2, result.vehicleCount)
        assertEquals(3, result.ruleCount)
    }

    @Test
    fun `claim updates records and returns counts`() = runTest {
        coEvery { accountDataDao.claimLegacyDrives("u1") } returns 5
        coEvery { accountDataDao.claimLegacyVehicles("u1") } returns 1
        coEvery { accountDataDao.claimLegacyClassificationRules("u1") } returns 2

        val result = useCase.claim("u1")

        assertEquals(5, result.driveCount)
        assertEquals(1, result.vehicleCount)
        assertEquals(2, result.ruleCount)
    }
}
