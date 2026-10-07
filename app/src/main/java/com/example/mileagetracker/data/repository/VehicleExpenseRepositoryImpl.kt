package com.example.mileagetracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.mileagetracker.data.local.dao.VehicleExpenseDao
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleExpenseRepositoryImpl @Inject constructor(
    private val dao: VehicleExpenseDao,
    @ApplicationContext context: Context?,
    syncRepository: SyncRepository,
    evidenceSyncStore: EvidenceSyncStore,
) : VehicleExpenseRepository {
    private val receiptStorage = ExpenseReceiptStorage(context)
    private val syncCoordinator = VehicleExpenseSyncCoordinator(
        dao = dao,
        syncRepository = syncRepository,
        evidenceSyncStore = evidenceSyncStore,
    )

    override suspend fun save(expense: VehicleExpense): VehicleExpense {
        val entity = expense.toEntity().copy(
            receiptAttachmentUri = expense.receiptAttachmentUri?.let(receiptStorage::copyToInternalStorage),
        )
        val rowId = dao.insert(entity)
        val saved = expense.copy(id = rowId.toInt(), receiptAttachmentUri = entity.receiptAttachmentUri)
        syncCoordinator.syncVehicleExpenses(saved.vehicleId)
        return saved
    }

    override suspend fun update(expense: VehicleExpense): Boolean {
        val entity = expense.toEntity().copy(
            receiptAttachmentUri = expense.receiptAttachmentUri?.let(receiptStorage::copyToInternalStorage),
        )
        val updated = dao.update(entity) > 0
        if (updated) syncCoordinator.syncVehicleExpenses(expense.vehicleId)
        return updated
    }

    override suspend fun deleteById(id: Int) {
        val existing = syncCoordinator.existingExpenseForSync(id)
        dao.deleteById(id)
        existing?.let {
            receiptStorage.delete(it.receiptAttachmentUri)
            syncCoordinator.syncVehicleExpenses(it.vehicleId)
        }
    }

    override suspend fun getById(id: Int): VehicleExpense? =
        dao.getById(id)?.toDomain()

    override fun observeForVehicle(vehicleId: Int): Flow<List<VehicleExpense>> =
        dao.getForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override fun observeForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<VehicleExpense>> =
        dao.getForVehicleInYear(vehicleId, fromMillis, toMillis)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getTotalForVehicleAndYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Double = dao.getTotalForVehicleAndYear(vehicleId, fromMillis, toMillis)

    constructor(dao: VehicleExpenseDao) : this(
        dao = dao,
        context = null,
        syncRepository = NoOpSyncRepository,
        evidenceSyncStore = NoOpEvidenceSyncStore,
    )
}

private class VehicleExpenseSyncCoordinator(
    private val dao: VehicleExpenseDao,
    private val syncRepository: SyncRepository,
    private val evidenceSyncStore: EvidenceSyncStore,
) {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    suspend fun existingExpenseForSync(id: Int): VehicleExpenseEntity? =
        if (syncEnabled) dao.getById(id) else null

    suspend fun syncVehicleExpenses(vehicleId: Int) {
        if (!syncEnabled) return
        val items = dao.getForVehicleOnce(vehicleId).map { entity ->
            val remotePath = entity.receiptAttachmentUri?.let {
                evidenceSyncStore.upload(it, expenseReceiptRelativePath(entity))
            }
            VehicleExpenseSyncItem(entity = entity, remotePath = remotePath)
        }
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.VEHICLE_EXPENSES,
            entityId = vehicleId.toString(),
            payloadJson = items.toVehicleExpenseSyncPayloadJson(),
        )
    }

    private fun expenseReceiptRelativePath(entity: VehicleExpenseEntity): String =
        "vehicle-expenses/${entity.vehicleId}/${entity.id}_receipt" +
            receiptExtension(entity.receiptAttachmentUri)

    private fun receiptExtension(uriString: String?): String {
        val path = Uri.parse(uriString).lastPathSegment ?: return ""
        val extension = path.substringAfterLast('.', "")
        return if (extension.isBlank()) "" else ".$extension"
    }
}

private class ExpenseReceiptStorage(
    private val context: Context?,
) {
    fun copyToInternalStorage(sourceUriString: String): String =
        context?.let { appContext ->
            val uri = sourceUriString.toUri()
            if (isExistingFileUri(uri)) {
                sourceUriString
            } else {
                copyToInternalStorage(appContext, uri)
            }
        } ?: sourceUriString

    fun delete(uriString: String?) {
        uriString ?: return
        val path = Uri.parse(uriString).path ?: return
        runCatching { File(path).delete() }
    }

    private fun isExistingFileUri(uri: Uri): Boolean =
        uri.scheme == "file" && File(uri.path ?: "").exists()

    private fun copyToInternalStorage(
        appContext: Context,
        uri: Uri,
    ): String {
        val dir = File(appContext.filesDir, "expense_receipts").also { it.mkdirs() }
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val dest = File(dir, "${System.currentTimeMillis()}.$extension")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest.toURI().toString()
    }
}

private fun VehicleExpense.toEntity() = VehicleExpenseEntity(
    id = id,
    vehicleId = vehicleId,
    categoryKey = category.key,
    amountCad = amountCad,
    date = date,
    vendorName = vendorName,
    notes = notes,
    receiptAttachmentUri = receiptAttachmentUri,
    gstHstAmount = gstHstAmount,
    isBusinessUse = isBusinessUse,
    createdAt = createdAt,
)

private fun VehicleExpenseEntity.toDomain() = VehicleExpense(
    id = id,
    vehicleId = vehicleId,
    category = ExpenseCategory.fromKey(categoryKey),
    amountCad = amountCad,
    date = date,
    vendorName = vendorName,
    notes = notes,
    receiptAttachmentUri = receiptAttachmentUri,
    gstHstAmount = gstHstAmount,
    isBusinessUse = isBusinessUse,
    businessUseAmount = 0.0,
    createdAt = createdAt,
)
