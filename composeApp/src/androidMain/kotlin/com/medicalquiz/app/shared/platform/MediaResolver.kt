package com.medicalquiz.app.shared.platform

import android.net.Uri
import android.provider.DocumentsContract

actual object MediaResolver {
    private var mediaIndex: Map<String, Uri> = emptyMap()

    actual fun init() {
        val treeUri = FolderPicker.getTreeDocumentUri() ?: return
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val mediaDocId = "$treeDocId/media"

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, mediaDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        val index = mutableMapOf<String, Uri>()
        AppContext.context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val docIdIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(docIdIndex)
                val name = cursor.getString(nameIndex)
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                index[name] = uri
            }
        }

        mediaIndex = index
    }

    actual fun hasMediaFile(fileName: String): Boolean = mediaIndex.containsKey(fileName)

    actual fun getMediaUri(fileName: String): String? = mediaIndex[fileName]?.toString()

    actual fun readMediaText(fileName: String): String? {
        val uri = mediaIndex[fileName] ?: return null
        return try {
            AppContext.context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        } catch (_: Exception) {
            null
        }
    }

    actual fun readMediaBytes(fileName: String): ByteArray? {
        val uri = mediaIndex[fileName] ?: return null
        return try {
            AppContext.context.contentResolver.openInputStream(uri)?.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    actual fun listMediaFiles(): List<String> = mediaIndex.keys.sorted()
}
