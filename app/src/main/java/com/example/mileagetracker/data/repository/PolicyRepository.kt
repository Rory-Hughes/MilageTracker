package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.PolicyTemplateDao
import com.example.mileagetracker.data.local.entity.toEntity
import com.example.mileagetracker.domain.model.PolicyTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that wraps [PolicyTemplateDao] and exposes domain-level operations.
 */
@Singleton
class PolicyRepository @Inject constructor(
    private val dao: PolicyTemplateDao
) {
    /** Observe all [PolicyTemplate]s as a hot [Flow]. */
    fun observePolicies(): Flow<List<PolicyTemplate>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    /** Upsert a [PolicyTemplate]. */
    suspend fun savePolicy(policy: PolicyTemplate) {
        dao.insertOrReplace(policy.toEntity())
    }

    /** Delete a [PolicyTemplate] by its id. */
    suspend fun deletePolicy(id: String) {
        dao.deleteById(id)
    }

    /** Return a single [PolicyTemplate] or null. */
    suspend fun getById(id: String): PolicyTemplate? =
        dao.getById(id)?.toDomain()
}
