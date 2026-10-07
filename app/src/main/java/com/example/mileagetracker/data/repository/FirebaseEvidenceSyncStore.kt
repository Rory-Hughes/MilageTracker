package com.example.mileagetracker.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseEvidenceSyncStore @Inject constructor(
    private val storage: FirebaseStorage,
    private val currentUserProvider: CurrentUserProvider,
    @ApplicationContext private val context: Context,
) : EvidenceSyncStore {
    override suspend fun upload(localUri: String?, relativePath: String): String? {
        if (localUri.isNullOrBlank()) return null
        val remotePath = evidencePath(relativePath)
        val input = openInputStream(localUri) ?: throw FileNotFoundException(localUri)
        input.use { stream ->
            storage.reference.child(remotePath).putStream(stream).await()
        }
        return remotePath
    }

    override suspend fun download(
        remotePath: String,
        localDirectory: String,
        localFileName: String,
    ): String {
        val dir = File(context.filesDir, localDirectory).also { it.mkdirs() }
        val safeName = localFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destination = File(dir, safeName)
        storage.reference.child(remotePath).getFile(destination).await()
        return destination.toURI().toString()
    }

    private fun evidencePath(relativePath: String): String {
        val uid = currentUserProvider.requireUid()
        return "users/$uid/evidence/$relativePath"
    }

    private fun openInputStream(uriString: String) = run {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).inputStream() }
            else -> context.contentResolver.openInputStream(uri)
        }
    }
}
