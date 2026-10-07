package com.example.mileagetracker.ui.export

import android.content.Context
import android.net.Uri
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Utility object for building and launching the Android share sheet for
 * exported logbook files (PDF and CSV).
 *
 * ## Usage
 * ```kotlin
 * val contentUri = ShareSheetHelper.getContentUri(context, file)
 * ShareSheetHelper.shareFile(
 *     context      = context,
 *     uri          = contentUri,
 *     mimeType     = "text/csv",
 *     chooserTitle = "Share Logbook CSV",
 * )
 * ```
 *
 * ## FileProvider authority
 * Uses `"${context.packageName}.provider"` — must match the authority
 * declared in `AndroidManifest.xml` and `res/xml/file_paths.xml`.
 *
 * ## Why not inline in the screen?
 * Keeping this logic in one place means both [ExportScreen] (fresh export)
 * and [ReportHistoryScreen] (re-share a previous export) use identical
 * intent-building code, preventing divergence in MIME types or flags.
 */
object ShareSheetHelper {

    /**
     * Converts a `file://` [File] into a `content://` [Uri] that external
     * apps can read via the app's [FileProvider].
     *
     * @param context  any context; used to resolve the package name.
     * @param file     the file to expose (must be under a path declared in
     *                 `res/xml/file_paths.xml`).
     * @return a `content://` URI grantable to share-sheet targets.
     */
    fun getContentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )

    /**
     * Converts a `file://` [Uri] into a `content://` [Uri].
     *
     * Convenience overload for callers that already hold a `file://` URI
     * (e.g. from [ExportUiState.Success.fileUri]).
     *
     * @param context  any context.
     * @param fileUri  a `file://` URI whose path points to a file under a
     *                 path declared in `res/xml/file_paths.xml`.
     * @return a `content://` URI, or the original URI if conversion fails.
     */
    fun getContentUri(context: Context, fileUri: Uri): Uri {
        val path = fileUri.path ?: return fileUri
        return getContentUri(context, File(path))
    }

    /**
     * Builds a [ShareCompat] chooser intent and launches the Android share
     * sheet immediately.
     *
     * The intent is built with:
     * - `ACTION_SEND` with the supplied [mimeType]
     * - `FLAG_GRANT_READ_URI_PERMISSION` on the stream URI
     * - A chooser title supplied by the caller so it is localised at the
     *   call site rather than hard-coded here
     *
     * @param context       activity or application context used to start the
     *                      chooser intent.
     * @param uri           a `content://` URI produced by [getContentUri].
     * @param mimeType      IANA MIME type — `"text/csv"` or
     *                      `"application/pdf"`.
     * @param chooserTitle  string shown as the share-sheet header.
     */
    fun shareFile(
        context: Context,
        uri: Uri,
        mimeType: String,
        chooserTitle: String,
    ) {
        ShareCompat.IntentBuilder(context)
            .setType(mimeType)
            .addStream(uri)
            .setChooserTitle(chooserTitle)
            .startChooser()
    }
}
