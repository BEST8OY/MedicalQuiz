package com.medicalquiz.app.shared.platform

import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object SafImporter {
    actual suspend fun importDatabases(): List<String> = withContext(Dispatchers.IO) {
        val treeUri = FolderPicker.getTreeDocumentUri() ?: return@withContext emptyList()
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val qbanksDocId = "$treeDocId/QBanks"

        val destDir = File(StorageProvider.getDatabaseDirectory())
        destDir.mkdirs()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, qbanksDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        val imported = mutableListOf<String>()
        AppContext.context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val docIdIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(docIdIndex)
                val name = cursor.getString(nameIndex)
                if (!name.endsWith(".db", ignoreCase = true)) continue

                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val destFile = File(destDir, name)

                try {
                    AppContext.context.contentResolver.openInputStream(fileUri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    imported.add(name)
                } catch (_: Exception) {
                }
            }
        }

        imported.sorted()
    }
}
