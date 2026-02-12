package com.medicalquiz.app.shared.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager

actual object TextIntentLauncher {
    actual fun openSelectedText(text: String): Boolean {
        val query = text.trim()
        if (query.isEmpty()) return false

        return try {
            val packageManager = AppContext.context.packageManager

            val processTextIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, query)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (canResolve(packageManager, processTextIntent)) {
                AppContext.context.startActivity(processTextIntent)
                return true
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (canResolve(packageManager, shareIntent)) {
                AppContext.context.startActivity(shareIntent)
                return true
            }

            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun canResolve(packageManager: PackageManager, intent: Intent): Boolean {
        return intent.resolveActivity(packageManager) != null
    }
}
