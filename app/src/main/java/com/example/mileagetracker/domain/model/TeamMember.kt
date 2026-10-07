package com.example.mileagetracker.domain.model

/**
 * Roles available within a team.
 */
enum class TeamRole {
    DRIVER,
    MANAGER,
    ADMIN
}

/**
 * Domain model representing a member of the organisation's team.
 *
 * @property userId        Firebase UID of the user.
 * @property email         Email address used for invite and sign-in.
 * @property displayName   Human-readable name shown in the UI.
 * @property role          The user's permission level within the team.
 * @property policyTemplateId  Optional ID of the [PolicyTemplate] assigned to this member.
 * @property departmentId  Optional identifier for the member's department/cost-centre.
 * @property isActive      False when the member has been deactivated but not deleted.
 */
data class TeamMember(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: TeamRole,
    val policyTemplateId: String? = null,
    val departmentId: String? = null,
    val isActive: Boolean = true
)
