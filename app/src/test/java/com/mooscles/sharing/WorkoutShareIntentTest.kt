package com.mooscles.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [HostFileProviderPaths::class], instrumentedPackages = ["androidx.core.content"])
class WorkoutShareIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun resetHostProviderRootCache() {
        // Instrumented AndroidX classes keep static state while Robolectric changes app cache paths.
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    @Test fun singleImageUsesReadableContentUriAndTemporaryReadGrant() {
        val file = image("one.png")
        val intent = WorkoutShareIntent.imageIntent(context, listOf(file), WorkoutShareIntent.WHATSAPP)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(WorkoutShareIntent.WHATSAPP, intent.`package`)
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.workout-shares", uri.authority)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals(1, intent.clipData!!.itemCount)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(file.readBytes(), it.readBytes()) }
    }

    @Test fun multipleImagesKeepTheirOrderAndGrantAccessToEveryPage() {
        val files = listOf(image("page-one.png"), image("page-two.png"), image("page-three.png"))
        val intent = WorkoutShareIntent.imageIntent(context, files)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/png", intent.type)
        val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals(3, uris.size)
        assertEquals(3, intent.clipData!!.itemCount)
        uris.forEachIndexed { index, uri ->
            assertEquals(uri, intent.clipData!!.getItemAt(index).uri)
            assertTrue(uri.lastPathSegment!!.endsWith(files[index].name))
        }
    }

    @Test fun providerIsPrivateAndRejectsFilesOutsideItsExportFolder() {
        @Suppress("DEPRECATION")
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.workout-shares", 0)!!
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val privateFile = File(context.cacheDir, "unrelated-private.png").apply { writeBytes(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { WorkoutShareIntent.imageIntent(context, listOf(privateFile)) }
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, "${context.packageName}.workout-shares", privateFile) }
        val escaped = Uri.parse("content://${context.packageName}.workout-shares/workout_cards/../unrelated-private.png")
        assertThrows(SecurityException::class.java) { context.contentResolver.openInputStream(escaped) }
        assertThrows(IllegalArgumentException::class.java) { WorkoutShareIntent.imageIntent(context, emptyList()) }
        val missingFile = File(context.cacheDir, "workout-shares/missing.png")
        assertThrows(IllegalArgumentException::class.java) { WorkoutShareIntent.imageIntent(context, listOf(missingFile)) }
    }

    @Test fun preparingAnOlderExportRenewsItsRetentionForTheReceiver() {
        val file = File(context.cacheDir, "workout-shares/export-${UUID.randomUUID()}/lease.png").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val old = System.currentTimeMillis() - 8 * 24 * 60 * 60_000L
        assertTrue(file.setLastModified(old))
        assertTrue(file.parentFile!!.setLastModified(old))
        val before = System.currentTimeMillis()
        WorkoutShareIntent.imageIntent(context, listOf(file))
        assertTrue(file.lastModified() >= before - 1_000)
        assertTrue(file.parentFile!!.lastModified() >= before - 1_000)
        WorkoutCardRenderer().cleanupObsoleteExports(context)
        assertTrue(file.exists())
    }

    @Test fun whatsappIsPreferredWithBusinessFallbackAndNoRecipientIsPreselected() {
        assertEquals(WorkoutShareIntent.WHATSAPP, WorkoutShareIntent.preferredWhatsApp(setOf(WorkoutShareIntent.WHATSAPP_BUSINESS, WorkoutShareIntent.WHATSAPP)))
        assertEquals(WorkoutShareIntent.WHATSAPP_BUSINESS, WorkoutShareIntent.preferredWhatsApp(setOf(WorkoutShareIntent.WHATSAPP_BUSINESS)))
        assertNull(WorkoutShareIntent.preferredWhatsApp(emptySet()))
        val destination = CapturedContext(context)
        assertTrue(WorkoutShareIntent.launch(destination, listOf(image("whatsapp.png")), "Share workout", WorkoutShareIntent.WHATSAPP))
        val intent = destination.started.single()
        assertEquals(WorkoutShareIntent.WHATSAPP, intent.`package`)
        assertNull(intent.data)
        assertNull(intent.getStringExtra("jid"))
        assertNull(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER))
    }

    @Test fun absentWhatsappOrLaunchFailureOpensAndroidChooser() {
        val files = listOf(image("fallback.png"))
        val destination = CapturedContext(context, rejectPackage = WorkoutShareIntent.WHATSAPP)
        assertTrue(WorkoutShareIntent.launch(destination, files, "Share workout", WorkoutShareIntent.WHATSAPP))
        assertEquals(2, destination.started.size)
        val chooser = destination.started.last()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val image = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, image.action)
        assertNull(image.`package`)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(image.clipData!!.getItemAt(0).uri, chooser.clipData!!.getItemAt(0).uri)

        val noWhatsapp = CapturedContext(context)
        assertTrue(WorkoutShareIntent.launch(noWhatsapp, files, "Share workout"))
        assertEquals(Intent.ACTION_CHOOSER, noWhatsapp.started.single().action)
        val noApps = CapturedContext(context, rejectAll = true)
        assertFalse(WorkoutShareIntent.launch(noApps, files, "Share workout"))
    }

    private fun image(name: String): File = File(context.cacheDir, "workout-shares/test-${UUID.randomUUID()}/$name").apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(1, 2, 3))
    }

    private class CapturedContext(base: Context, val rejectPackage: String? = null, val rejectAll: Boolean = false) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            started += intent
            if (rejectAll || (rejectPackage != null && intent.`package` == rejectPackage)) throw ActivityNotFoundException()
        }
    }
}

/**
 * AndroidX's actual provider is exercised above. Only its POSIX-only boundary predicate needs
 * host-separator normalization when Robolectric runs on Windows. No production code is shadowed.
 */
@Implements(className = "androidx.core.content.FileProvider\$SimplePathStrategy", isInAndroidSdk = false)
class HostFileProviderPaths {
    @Implementation
    protected fun belongsToRoot(filePath: String, rootPath: String): Boolean {
        val file = filePath.replace(File.separatorChar, '/').trimEnd('/')
        val root = rootPath.replace(File.separatorChar, '/').trimEnd('/')
        return file == root || file.startsWith("$root/")
    }
}
