package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.UserClassificationRuleDao
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserClassificationRuleRepositoryImpl @Inject constructor(
    private val dao: UserClassificationRuleDao,
    private val currentUserProvider: CurrentUserProvider,
    private val syncRepository: com.example.mileagetracker.domain.repository.SyncRepository,
) : UserClassificationRuleRepository {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    override fun observeAll(): Flow<List<UserRule>> =
        dao.observeAll(currentUserProvider.requireUid()).map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledRules(): List<UserRule> =
        dao.getEnabledRules(currentUserProvider.requireUid()).map { it.toDomain() }

    override suspend fun save(rule: UserRule): UserRule {
        val ownerUid = currentUserProvider.requireUid()
        val entity = rule.toEntity()
        val rowId = dao.upsert(entity)
        // rowId == id for autoGenerate PKs when inserting; for updates the
        // existing id is preserved via REPLACE strategy.
        val savedId = if (rule.id == 0) rowId.toInt() else rule.id
        syncAllRules(ownerUid)
        return rule.copy(id = savedId)
    }

    override suspend fun deleteById(id: Int) {
        val ownerUid = currentUserProvider.requireUid()
        dao.deleteById(ownerUid, id)
        syncAllRules(ownerUid)
    }

    private suspend fun syncAllRules(ownerUid: String) {
        if (!syncEnabled) return
        val rules = dao.getAllOnce(ownerUid)
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.USER_CLASSIFICATION_RULES,
            entityId = "all",
            payloadJson = rules.toUserClassificationRulesPayloadJson(),
        )
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private fun UserClassificationRuleEntity.toDomain(): UserRule {
        val resolvedCategory = runCatching {
            TripCategory.valueOf(category)
        }.getOrDefault(TripCategory.CUSTOM)

        return UserRule(
            id = id,
            name = name,
            keyword = keyword,
            category = resolvedCategory,
            confidence = confidence,
            priority = priority,
            isEnabled = isEnabled,
            createdAt = createdAt,
        )
    }

    private fun UserRule.toEntity() = UserClassificationRuleEntity(
        id = id,
        ownerUid = currentUserProvider.requireUid(),
        name = name,
        keyword = keyword,
        category = category.name,
        confidence = confidence,
        priority = priority,
        isEnabled = isEnabled,
        createdAt = createdAt,
    )

    constructor(
        dao: UserClassificationRuleDao,
        currentUserProvider: CurrentUserProvider,
    ) : this(dao, currentUserProvider, NoOpSyncRepository)
}
