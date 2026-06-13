package com.medicalquiz.app.shared.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

actual object FolderPicker {
    private const val PREFS_NAME = "saf_folder_picker"
    private const val KEY_TREE_URI = "tree_uri"

    actual fun hasPersistedFolder(): Boolean {
        val uriString = getPersistedTreeUriString() ?: return false
        return try {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.isDocumentUri(AppContext.context, uri) ||
                docId.contains(":")
        } catch (_: Exception) {
            false
        }
    }

    actual fun getPersistedTreeUriString(): String? {
        val prefs = AppContext.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TREE_URI, null)
    }

    actual fun saveTreeUri(uriString: String) {
        val uri = Uri.parse(uriString)
        val prefs = AppContext.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TREE_URI, uriString).apply()
        takePersistablePermission(uri)
    }

    fun saveTreeUri(uri: Uri) {
        saveTreeUri(uri.toString())
    }

    fun clearTreeUri() {
        val prefs = AppContext.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            AppContext.context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    fun getTreeDocumentUri(): Uri? {
        val uriString = getPersistedTreeUriString() ?: return null
        return try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            null
        }
    }

    fun buildDocumentUri(childName: String): Uri? {
        val treeUri = getTreeDocumentUri() ?: return null
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childDocId = "$treeDocId/$childName"
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
    }

    fun listChildren(path: String = ""): List<DocumentFile> {
        val treeUri = getTreeDocumentUri() ?: return emptyList()
        val treeDoc = DocumentFile.fromTreeUri(AppContext.context, treeUri) ?: return emptyList()
        return if (path.isEmpty()) {
            treeDoc.listFiles().toList()
        } else {
            val parts = path.split("/")
            var current = treeDoc
            for (part in parts) {
                current = current.findFile(part) ?: return emptyList()
            }
            if (current.isDirectory) current.listFiles().toList() else emptyList()
        }
    }
}
