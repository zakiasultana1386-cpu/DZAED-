package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CopiedProof(
    val filePath: String,
    val mimeType: String,
    val displayName: String
)

object ProofStorageHelper {
    suspend fun copyProofToInternalStorage(context: Context, uri: Uri): CopiedProof? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val displayName = queryDisplayName(context, uri)
                    ?: "voucher_${System.currentTimeMillis()}"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    ?: displayName.substringAfterLast('.', "dat")

                val proofsDir = File(context.filesDir, "proofs").apply { mkdirs() }
                val destFile = File(proofsDir, "proof_${System.currentTimeMillis()}.$ext")

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null

                CopiedProof(
                    filePath = destFile.absolutePath,
                    mimeType = mimeType,
                    displayName = displayName
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return null
    }
}
