package com.example.mileagetracker.domain.model

/**
 * Bump this version whenever the cloud-upload disclosure materially changes.
 * Existing users must explicitly accept the new disclosure before uploads resume.
 */
object CloudUploadConsent {
    const val CURRENT_VERSION = 1
}

class CloudUploadConsentRequiredException :
    IllegalStateException("Accept cloud backup in Settings before uploading mileage records.")
