package com.example.mileagetracker.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataStorageInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun countAttachmentFiles(): Int = countFiles("attachments")

    fun countOdometerPhotoFiles(): Int = countFiles("odometer_photos")

    fun countExpenseReceiptFiles(): Int = countFiles("expense_receipts")

    private fun countFiles(directoryName: String): Int {
        val directory = File(context.filesDir, directoryName)
        if (!directory.exists()) return 0
        return directory.walkTopDown().count { it.isFile }
    }
}
