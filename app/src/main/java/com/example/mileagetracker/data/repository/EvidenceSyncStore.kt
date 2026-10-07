package com.example.mileagetracker.data.repository

interface EvidenceSyncStore {
    suspend fun upload(localUri: String?, relativePath: String): String?

    suspend fun download(
        remotePath: String,
        localDirectory: String,
        localFileName: String,
    ): String
}

object NoOpEvidenceSyncStore : EvidenceSyncStore {
    override suspend fun upload(localUri: String?, relativePath: String): String? = null

    override suspend fun download(
        remotePath: String,
        localDirectory: String,
        localFileName: String,
    ): String = ""
}
