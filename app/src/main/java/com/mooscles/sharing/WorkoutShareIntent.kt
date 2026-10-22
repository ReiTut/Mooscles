package com.mooscles.sharing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Grants access only to the selected cached PNGs, for the lifetime Android gives the receiver. */
object WorkoutShareIntent {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun imageIntent(context: Context, files: List<File>, packageName: String? = null): Intent {
        require(files.isNotEmpty()) { "There are no workout images to share." }
        val root = File(context.cacheDir, "workout-shares").canonicalFile
        val now = System.currentTimeMillis()
        val uris = files.map { file ->
            val canonical = file.canonicalFile
            require(canonical.isFile && canonical.extension.equals("png", ignoreCase = true) &&
                canonical.path.startsWith(root.path + File.separator)) { "Workout image is unavailable." }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.workout-shares", canonical)
            // Renew an export retained in a long-lived preview before another app receives it.
            canonical.setLastModified(now)
            canonical.parentFile?.takeIf { it.canonicalFile != root }?.setLastModified(now)
            uri
        }
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newRawUri("Workout image", uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            packageName?.let(::setPackage)
        }
    }

    fun availableWhatsAppPackages(context: Context): Set<String> =
        listOf(WHATSAPP, WHATSAPP_BUSINESS).filter { packageName ->
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setPackage(packageName)
            }, 0) != null
        }.toSet()

    fun preferredWhatsApp(available: Set<String>): String? = when {
        WHATSAPP in available -> WHATSAPP
        WHATSAPP_BUSINESS in available -> WHATSAPP_BUSINESS
        else -> null
    }

    /** Opens a destination, never a particular recipient and never sends without the user's action. */
    fun launch(context: Context, files: List<File>, title: String, preferredPackage: String? = null): Boolean {
        val share = imageIntent(context, files)
        if (preferredPackage != null) {
            try {
                start(context, Intent(share).setPackage(preferredPackage))
                return true
            } catch (_: ActivityNotFoundException) {
                // An app can be uninstalled after preview, or cannot handle a multipage image.
            } catch (_: SecurityException) {
                // A restricted destination can still be shared through another Android app.
            }
        }
        return try {
            start(context, Intent.createChooser(share, title).apply {
                clipData = share.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun start(context: Context, intent: Intent) {
        var current = context
        while (current is ContextWrapper && current !is Activity && current.baseContext !== current) {
            current = current.baseContext
        }
        if (current !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
