package com.example.mileagetracker.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Shell-level ViewModel that resolves the signed-in Firebase user to a
 * [TeamMember] domain object and observes the fleet of active drivers.
 *
 * ## Firestore data shape
 * Documents are stored at `team/{uid}` with the following fields:
 * ```
 * displayName:      String
 * email:            String
 * role:             String  ("DRIVER" | "MANAGER" | "ADMIN")
 * policyTemplateId: String? (nullable)
 * departmentId:     String? (nullable)
 * isActive:         Boolean (defaults true)
 * ```
 */
@HiltViewModel
class TeamSessionViewModel @Inject constructor(
    private val currentUserProvider: CurrentUserProvider,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _currentMember = MutableStateFlow<TeamMember?>(null)
    val currentMember: StateFlow<TeamMember?> = _currentMember.asStateFlow()

    private val _managedDrivers = MutableStateFlow<List<TeamMember>>(emptyList())
    val managedDrivers: StateFlow<List<TeamMember>> = _managedDrivers.asStateFlow()

    init {
        loadSession()
    }

    /** Resolves the current Firebase user to a [TeamMember]. Safe to call again after a role change. */
    fun loadSession() {
        viewModelScope.launch {
            val currentUser = currentUserProvider.currentUser() ?: return@launch
            val member = resolveMember(currentUser)
            _currentMember.value = member
            loadManagedDriversIfNeeded(member)
        }
    }

    private suspend fun loadManagedDrivers() {
        val snapshot = fetchManagedDriversSnapshot() ?: return
        _managedDrivers.value = snapshot.documents.map(::driverFromDocument)
    }

    private suspend fun resolveMember(currentUser: CurrentUser): TeamMember {
        val snapshot = fetchCurrentMemberSnapshot(currentUser.uid)
        return snapshot
            ?.takeIf(DocumentSnapshot::exists)
            ?.toTeamMember(currentUser)
            ?: currentUser.toSoloDriver()
    }

    private suspend fun fetchCurrentMemberSnapshot(uid: String): DocumentSnapshot? =
        runCatching {
            firestore.collection("team")
                .document(uid)
                .get()
                .await()
        }.getOrNull()

    private suspend fun fetchManagedDriversSnapshot(): QuerySnapshot? =
        runCatching {
            firestore.collection("team")
                .whereEqualTo("role", TeamRole.DRIVER.name)
                .whereEqualTo("isActive", true)
                .get()
                .await()
        }.getOrNull()

    private fun loadManagedDriversIfNeeded(member: TeamMember) {
        if (member.role == TeamRole.MANAGER || member.role == TeamRole.ADMIN) {
            viewModelScope.launch {
                loadManagedDrivers()
            }
        }
    }

    private fun DocumentSnapshot.toTeamMember(currentUser: CurrentUser): TeamMember =
        TeamMember(
            userId = currentUser.uid,
            email = currentUser.email ?: "",
            displayName = getString("displayName")
                ?: currentUser.displayName
                ?: currentUser.email
                ?: UNKNOWN_MEMBER_NAME,
            role = parseRole(getString("role")),
            policyTemplateId = getString("policyTemplateId"),
            departmentId = getString("departmentId"),
            isActive = getBoolean("isActive") ?: true,
        )

    private fun driverFromDocument(doc: DocumentSnapshot): TeamMember =
        TeamMember(
            userId = doc.id,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: doc.id,
            role = TeamRole.DRIVER,
            policyTemplateId = doc.getString("policyTemplateId"),
            departmentId = doc.getString("departmentId"),
            isActive = doc.getBoolean("isActive") ?: true,
        )

    private fun CurrentUser.toSoloDriver(): TeamMember =
        TeamMember(
            userId = uid,
            email = email ?: "",
            displayName = displayName ?: email ?: UNKNOWN_MEMBER_NAME,
            role = TeamRole.DRIVER,
        )

    private fun parseRole(roleValue: String?): TeamRole =
        roleValue?.let { runCatching { TeamRole.valueOf(it) }.getOrNull() } ?: TeamRole.DRIVER

    private companion object {
        const val UNKNOWN_MEMBER_NAME = "Unknown"
    }
}
