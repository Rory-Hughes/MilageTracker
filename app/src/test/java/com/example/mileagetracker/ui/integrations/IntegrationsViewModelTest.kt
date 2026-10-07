package com.example.mileagetracker.ui.integrations

import android.net.Uri
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.data.repository.IntegrationRepository
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.IntegrationConfig
import com.example.mileagetracker.domain.model.IntegrationProvider
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import com.example.mileagetracker.domain.usecase.ExportPayrollSummaryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationsViewModelTest {

    private lateinit var integrationRepository: IntegrationRepository
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var driveRepository: DriveRepository
    private lateinit var vehicleExpenseRepository: VehicleExpenseRepository
    private lateinit var exportPayrollSummaryUseCase: ExportPayrollSummaryUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        integrationRepository = mockk(relaxed = true)
        approvalRepository = mockk(relaxed = true)
        driveRepository = mockk(relaxed = true)
        vehicleExpenseRepository = mockk(relaxed = true)
        exportPayrollSummaryUseCase = mockk(relaxed = true)

        every { integrationRepository.getAllConfigs() } returns emptyList()
        every { integrationRepository.getConfig(any()) } returns null
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns emptyList()
        coEvery {
            exportPayrollSummaryUseCase.execute(any(), any())
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "No approved records found for the selected period.",
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads integrations from repository`() = runTest {
        val configs = listOf(config(IntegrationProvider.QUICKBOOKS))
        every { integrationRepository.getAllConfigs() } returns configs

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(configs, viewModel.integrations.value)
    }

    @Test
    fun `loadIntegrations refreshes list from repository`() = runTest {
        var configs = listOf(config(IntegrationProvider.QUICKBOOKS))
        every { integrationRepository.getAllConfigs() } answers { configs }
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(IntegrationProvider.QUICKBOOKS, viewModel.integrations.value.single().provider)

        configs = listOf(config(IntegrationProvider.XERO))
        viewModel.loadIntegrations()
        advanceUntilIdle()

        assertEquals(IntegrationProvider.XERO, viewModel.integrations.value.single().provider)
    }

    @Test
    fun `enableCsvExport saves payroll config and emits connected state`() = runTest {
        var configs = emptyList<IntegrationConfig>()
        every { integrationRepository.getAllConfigs() } answers { configs }
        every { integrationRepository.saveConfig(any()) } answers {
            configs = listOf(firstArg())
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enableCsvExport()
        advanceUntilIdle()

        verify {
            integrationRepository.saveConfig(
                match {
                    it.provider == IntegrationProvider.PAYROLL_CSV &&
                        it.accessToken == "" &&
                        it.lastSyncAt == null
                }
            )
        }
        assertEquals(
            IntegrationsViewModel.SyncState.Connected(IntegrationProvider.PAYROLL_CSV),
            viewModel.syncState.value,
        )
        assertEquals(IntegrationProvider.PAYROLL_CSV, viewModel.integrations.value.single().provider)
    }

    @Test
    fun `disconnect revokes provider and emits disconnected state`() = runTest {
        var configs = listOf(config(IntegrationProvider.XERO))
        every { integrationRepository.getAllConfigs() } answers { configs }
        every { integrationRepository.revokeConfig(IntegrationProvider.XERO) } answers {
            configs = emptyList()
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.disconnect(IntegrationProvider.XERO)
        advanceUntilIdle()

        verify(exactly = 1) { integrationRepository.revokeConfig(IntegrationProvider.XERO) }
        assertTrue(viewModel.integrations.value.isEmpty())
        assertEquals(
            IntegrationsViewModel.SyncState.Disconnected(IntegrationProvider.XERO),
            viewModel.syncState.value,
        )
    }

    @Test
    fun `syncNow csv ready updates last synced and emits csv ready`() = runTest {
        val existing = config(IntegrationProvider.PAYROLL_CSV, lastSyncAt = null)
        every { integrationRepository.getConfig(IntegrationProvider.PAYROLL_CSV) } returns existing
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 7))
        coEvery { driveRepository.getDriveById(7) } returns drive(id = 7, distanceKm = 12f, rate = 0.70f)
        val uri = mockk<Uri>()
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, any())
        } returns ExportPayrollSummaryUseCase.ExportResult.CsvReady(uri)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        verify {
            integrationRepository.saveConfig(
                match {
                    it.provider == IntegrationProvider.PAYROLL_CSV &&
                        it.lastSyncAt != null
                }
            )
        }
        assertEquals(IntegrationsViewModel.SyncState.CsvReady(uri), viewModel.syncState.value)
    }

    @Test
    fun `syncNow sync success updates last synced and emits success state`() = runTest {
        val existing = config(IntegrationProvider.QUICKBOOKS, lastSyncAt = null)
        every { integrationRepository.getConfig(IntegrationProvider.QUICKBOOKS) } returns existing
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 8))
        coEvery { driveRepository.getDriveById(8) } returns drive(id = 8, distanceKm = 5f, rate = 0.60f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.QUICKBOOKS, any())
        } returns ExportPayrollSummaryUseCase.ExportResult.SyncSuccess(
            provider = IntegrationProvider.QUICKBOOKS,
            recordCount = 3,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.QUICKBOOKS)
        advanceUntilIdle()

        verify {
            integrationRepository.saveConfig(
                match {
                    it.provider == IntegrationProvider.QUICKBOOKS &&
                        it.lastSyncAt != null
                }
            )
        }
        assertEquals(
            IntegrationsViewModel.SyncState.SyncSuccess(IntegrationProvider.QUICKBOOKS, 3),
            viewModel.syncState.value,
        )
    }

    @Test
    fun `syncNow error emits error state without touching last synced config`() = runTest {
        every { integrationRepository.getConfig(IntegrationProvider.XERO) } returns config(IntegrationProvider.XERO)
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 5))
        coEvery { driveRepository.getDriveById(5) } returns drive(id = 5, distanceKm = 9f, rate = 0.62f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.XERO, any())
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.XERO,
            message = "sync failed",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.XERO)
        advanceUntilIdle()

        verify(exactly = 0) { integrationRepository.saveConfig(any()) }
        assertEquals(
            IntegrationsViewModel.SyncState.Error(IntegrationProvider.XERO, "sync failed"),
            viewModel.syncState.value,
        )
    }

    @Test
    fun `syncNow builds payroll rows from approved trip and business expense`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        val approvals = listOf(
            approvedTrip(submittedByUserId = 42, tripId = 7),
            approvedExpense(submittedByUserId = 42, expenseId = 8),
            approvedExpense(submittedByUserId = 42, expenseId = 9),
        )
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns approvals
        coEvery { driveRepository.getDriveById(7) } returns drive(id = 7, distanceKm = 10f, rate = 0.70f)
        coEvery { vehicleExpenseRepository.getById(8) } returns expense(id = 8, amount = 15.5, isBusinessUse = true)
        coEvery { vehicleExpenseRepository.getById(9) } returns expense(id = 9, amount = 99.0, isBusinessUse = false)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "test",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        val row = rowsSlot.captured.single()
        assertEquals("Driver 42", row.employeeName)
        assertEquals(10.0, row.totalKm, 0.0001)
        assertEquals(22.5, row.reimbursementAmountCad, 0.0001)
        assertEquals(70, row.policyRateCentsPerKm)
    }

    @Test
    fun `syncNow emits syncing state before export result returns`() = runTest {
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 3))
        coEvery { driveRepository.getDriveById(3) } returns drive(id = 3, distanceKm = 2f, rate = 0.50f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, any())
        } coAnswers {
            delay(1_000)
            ExportPayrollSummaryUseCase.ExportResult.Error(
                provider = IntegrationProvider.PAYROLL_CSV,
                message = "delayed",
            )
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        runCurrent()
        assertEquals(
            IntegrationsViewModel.SyncState.Syncing(IntegrationProvider.PAYROLL_CSV),
            viewModel.syncState.value,
        )

        advanceUntilIdle()
        assertEquals(
            IntegrationsViewModel.SyncState.Error(IntegrationProvider.PAYROLL_CSV, "delayed"),
            viewModel.syncState.value,
        )
    }

    @Test
    fun `syncNow error keeps integrations unchanged`() = runTest {
        val configs = listOf(config(IntegrationProvider.PAYROLL_CSV))
        every { integrationRepository.getAllConfigs() } returns configs
        every {
            integrationRepository.getConfig(IntegrationProvider.PAYROLL_CSV)
        } returns config(IntegrationProvider.PAYROLL_CSV)
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 4))
        coEvery { driveRepository.getDriveById(4) } returns drive(id = 4, distanceKm = 8f, rate = 0.66f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, any())
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "nope",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val before = viewModel.integrations.value
        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        assertEquals(before, viewModel.integrations.value)
    }

    @Test
    fun `syncNow with no approved rows exports empty payroll rows`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns emptyList()
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "none",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, any())
        }
        assertTrue(rowsSlot.captured.isEmpty())
    }

    @Test
    fun `syncNow omits row when trip is missing and no business expense exists`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 700))
        coEvery { driveRepository.getDriveById(700) } returns null
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "missing drive",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        assertTrue(rowsSlot.captured.isEmpty())
    }

    @Test
    fun `syncNow ignores missing expense record while keeping trip reimbursement`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery {
            approvalRepository.getApprovedInRange(any(), any())
        } returns listOf(
            approvedTrip(submittedByUserId = 55, tripId = 11),
            approvedExpense(submittedByUserId = 55, expenseId = 12),
        )
        coEvery { driveRepository.getDriveById(11) } returns drive(id = 11, distanceKm = 10f, rate = 0.50f)
        coEvery { vehicleExpenseRepository.getById(12) } returns null
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "ok",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        val row = rowsSlot.captured.single()
        assertEquals(10.0, row.totalKm, 0.0001)
        assertEquals(5.0, row.reimbursementAmountCad, 0.0001)
    }

    @Test
    fun `syncNow uses reimbursement snapshot when drive snapshot exists`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 14))
        coEvery {
            driveRepository.getDriveById(14)
        } returns drive(
            id = 14,
            distanceKm = 20f,
            rate = 0.80f,
            reimbursementSnapshot = 4.25f,
        )
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "ok",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        assertEquals(4.25, rowsSlot.captured.single().reimbursementAmountCad, 0.0001)
    }

    @Test
    fun `syncNow rounds average policy rate cents across multiple trips`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery {
            approvalRepository.getApprovedInRange(any(), any())
        } returns listOf(
            approvedTrip(submittedByUserId = 91, tripId = 30),
            approvedTrip(submittedByUserId = 91, tripId = 31),
        )
        coEvery { driveRepository.getDriveById(30) } returns drive(id = 30, distanceKm = 1f, rate = 0.69f)
        coEvery { driveRepository.getDriveById(31) } returns drive(id = 31, distanceKm = 1f, rate = 0.70f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "ok",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        assertEquals(70, rowsSlot.captured.single().policyRateCentsPerKm)
    }

    @Test
    fun `syncNow includes expense-only payroll row for business expense`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(
            approvedExpense(submittedByUserId = 77, expenseId = 400),
        )
        coEvery {
            vehicleExpenseRepository.getById(400)
        } returns expense(id = 400, amount = 18.75, isBusinessUse = true)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "ok",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        val row = rowsSlot.captured.single()
        assertEquals(0.0, row.totalKm, 0.0001)
        assertEquals(18.75, row.reimbursementAmountCad, 0.0001)
        assertEquals(0, row.policyRateCentsPerKm)
    }

    @Test
    fun `syncNow creates separate rows for different users`() = runTest {
        val rowsSlot = slot<List<ExportPayrollSummaryUseCase.PayrollRow>>()
        coEvery {
            approvalRepository.getApprovedInRange(any(), any())
        } returns listOf(
            approvedTrip(submittedByUserId = 1, tripId = 51),
            approvedTrip(submittedByUserId = 2, tripId = 52),
        )
        coEvery { driveRepository.getDriveById(51) } returns drive(id = 51, distanceKm = 4f, rate = 0.50f)
        coEvery { driveRepository.getDriveById(52) } returns drive(id = 52, distanceKm = 6f, rate = 0.60f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.PAYROLL_CSV, capture(rowsSlot))
        } returns ExportPayrollSummaryUseCase.ExportResult.Error(
            provider = IntegrationProvider.PAYROLL_CSV,
            message = "ok",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.PAYROLL_CSV)
        advanceUntilIdle()

        val rows = rowsSlot.captured
        assertEquals(2, rows.size)
        assertEquals(setOf("Driver 1", "Driver 2"), rows.map { it.employeeName }.toSet())
    }

    @Test
    fun `syncNow success without existing config skips last-sync save`() = runTest {
        every { integrationRepository.getConfig(IntegrationProvider.QUICKBOOKS) } returns null
        coEvery { approvalRepository.getApprovedInRange(any(), any()) } returns listOf(approvedTrip(tripId = 81))
        coEvery { driveRepository.getDriveById(81) } returns drive(id = 81, distanceKm = 7f, rate = 0.60f)
        coEvery {
            exportPayrollSummaryUseCase.execute(IntegrationProvider.QUICKBOOKS, any())
        } returns ExportPayrollSummaryUseCase.ExportResult.SyncSuccess(
            provider = IntegrationProvider.QUICKBOOKS,
            recordCount = 1,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow(IntegrationProvider.QUICKBOOKS)
        advanceUntilIdle()

        verify(exactly = 0) { integrationRepository.saveConfig(any()) }
        assertEquals(
            IntegrationsViewModel.SyncState.SyncSuccess(IntegrationProvider.QUICKBOOKS, 1),
            viewModel.syncState.value,
        )
    }

    @Test
    fun `setSyncError emits error state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSyncError(IntegrationProvider.QUICKBOOKS, "boom")

        assertEquals(
            IntegrationsViewModel.SyncState.Error(IntegrationProvider.QUICKBOOKS, "boom"),
            viewModel.syncState.value,
        )
    }

    @Test
    fun `resetSyncState returns state to idle`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setSyncError(IntegrationProvider.QUICKBOOKS, "boom")
        assertTrue(viewModel.syncState.value is IntegrationsViewModel.SyncState.Error)

        viewModel.resetSyncState()

        assertEquals(IntegrationsViewModel.SyncState.Idle, viewModel.syncState.value)
    }

    private fun createViewModel(): IntegrationsViewModel = IntegrationsViewModel(
        integrationRepository = integrationRepository,
        approvalRepository = approvalRepository,
        driveRepository = driveRepository,
        vehicleExpenseRepository = vehicleExpenseRepository,
        exportPayrollSummaryUseCase = exportPayrollSummaryUseCase,
    )

    private fun config(
        provider: IntegrationProvider,
        lastSyncAt: Long? = 1_700_000_100_000L,
    ) = IntegrationConfig(
        provider = provider,
        accessToken = "token",
        refreshToken = "refresh",
        connectedAt = 1_700_000_000_000L,
        lastSyncAt = lastSyncAt,
    )

    private fun approvedTrip(
        submittedByUserId: Long = 11,
        tripId: Long,
    ) = ApprovalRequest(
        submittedByUserId = submittedByUserId,
        submittedAt = 1_700_000_000_000L,
        status = ApprovalStatus.APPROVED,
        tripId = tripId,
    )

    private fun approvedExpense(
        submittedByUserId: Long = 11,
        expenseId: Long,
    ) = ApprovalRequest(
        submittedByUserId = submittedByUserId,
        submittedAt = 1_700_000_000_000L,
        status = ApprovalStatus.APPROVED,
        expenseId = expenseId,
    )

    private fun drive(
        id: Int,
        distanceKm: Float,
        rate: Float,
        reimbursementSnapshot: Float? = null,
    ) = DriveEntity(
        id = id,
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_060_000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = distanceKm,
        driverUid = "uid",
        tripCategory = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        appliedRateSnapshot = rate,
        reimbursementAmountSnapshot = reimbursementSnapshot,
        trackingSource = TrackingSource.AUTO_TRACKED,
    )

    private fun expense(
        id: Int,
        amount: Double,
        isBusinessUse: Boolean,
    ) = VehicleExpense(
        id = id,
        vehicleId = 1,
        category = ExpenseCategory.Fuel,
        amountCad = amount,
        date = 1_700_000_000_000L,
        isBusinessUse = isBusinessUse,
    )
}
