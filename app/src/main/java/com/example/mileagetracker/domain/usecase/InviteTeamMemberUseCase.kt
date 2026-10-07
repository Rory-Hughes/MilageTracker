package com.example.mileagetracker.domain.usecase

import android.content.Context
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Sends a Firebase Auth email-link (passwordless sign-in) invite to [email]
 * and returns a *pending* [TeamMember] record so the UI can give optimistic feedback.
 *
 * ## How it works
 * 1. An [ActionCodeSettings] object is built with the app's deep-link URL, which
 *    carries `role`, `policyId`, and `pendingId` as query parameters.
 * 2. [FirebaseAuth.sendSignInLinkToEmail] dispatches the email; Firebase delivers
 *    a magic-link that opens the app on the invitee's device.
 * 3. On first sign-in the app (or a Cloud Function) reads the query params from
 *    the deep link and activates the [TeamMember] record in Firestore.
 *
 * Firebase Dynamic Links was shut down in August 2025 and is **not** used here.
 * The continuation URL below (`DEEP_LINK_BASE`) must be an HTTPS domain registered
 * in your Firebase project's "Authorised domains" list in Authentication settings.
 *
 * @throws IllegalArgumentException if [email] is blank or lacks '@'.
 * @throws IllegalStateException    if no admin is currently signed in.
 */
class InviteTeamMemberUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) {
    companion object {
        /**
         * HTTPS domain registered in Firebase Console -> Authentication ->
         * Settings -> Authorised domains.  Must also be listed in
         * AndroidManifest.xml as an intent-filter host for the main Activity.
         */
        private const val DEEP_LINK_BASE = "https://mileagetracker.app/invite"

        /** Android package name used in [ActionCodeSettings]. */
        private const val ANDROID_PACKAGE = "com.example.mileagetracker"
    }

    /**
     * @param email            Invitee's email address.
     * @param role             Role to assign when the invitee accepts.
     * @param policyTemplateId Optional policy template id to assign on accept.
     * @return A pending [TeamMember] with [TeamMember.isActive] = false.
     */
    suspend operator fun invoke(
        email: String,
        role: TeamRole,
        policyTemplateId: String? = null,
    ): TeamMember {
        require(email.isNotBlank() && email.contains('@')) {
            "A valid email address is required."
        }
        val admin = auth.currentUser
            ?: error("No authenticated admin -- cannot send invite.")

        val pendingId = UUID.randomUUID().toString()

        // Build the continuation URL with invite metadata as query params.
        val continueUrl = buildString {
            append(DEEP_LINK_BASE)
            append("?pendingId=").append(pendingId)
            append("&role=").append(role.name)
            append("&invitedBy=").append(admin.uid)
            policyTemplateId?.let { append("&policyId=").append(it) }
        }

        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl(continueUrl)
            .setHandleCodeInApp(true) // open link directly in the app
            .setAndroidPackageName(
                ANDROID_PACKAGE,
                true, // installIfNotAvailable
                null, // minimumVersion -- null = any version
            )
            .build()

        // Fire-and-forget; the Task completes asynchronously.
        // To await completion in a coroutine, add the dependency:
        //   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.x")
        // then call: auth.sendSignInLinkToEmail(email, actionCodeSettings).await()
        auth.sendSignInLinkToEmail(email, actionCodeSettings)

        return TeamMember(
            userId = pendingId,
            email = email,
            displayName = "",
            role = role,
            policyTemplateId = policyTemplateId,
            isActive = false,
        )
    }
}
