package com.mooscles.photos

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.mooscles.sharing.HostFileProviderPaths
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/** Photos need no permission: the camera goes through the system app and the file through a private FileProvider path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [HostFileProviderPaths::class], instrumentedPackages = ["androidx.core.content"])
class PhotoManifestHostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun manifest() = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml")).documentElement

    @Before fun resetProviderRootCache() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    @Test fun noCameraPermission() {
        val declared = manifest().getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        }
        val forbidden = declared.filter { it.endsWith(".CAMERA") || it.contains("READ_MEDIA_") || it.endsWith("READ_EXTERNAL_STORAGE") || it.endsWith("WRITE_EXTERNAL_STORAGE") }
        assertEquals(emptyList<String>(), forbidden)
        val merged = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertEquals(emptyList<String>(), merged.filter { it.endsWith(".CAMERA") || it.contains("READ_MEDIA_") })
    }

    @Test fun captureQueryIsDeclared() {
        val queries = manifest().getElementsByTagName("queries").item(0) as Element
        val actions = queries.getElementsByTagName("action").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        }
        assertTrue(actions.toString(), "android.media.action.IMAGE_CAPTURE" in actions)
    }

    @Test fun capturePathIsShared() {
        val file = File(context.cacheDir, "$PHOTO_CAPTURE_DIR/x.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.workout-shares", file)
        assertEquals("content", uri.scheme)
        assertEquals("/photo_capture/x.jpg", uri.path)
        context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(file.readBytes(), it.readBytes()) }
        val outside = File(context.filesDir, "exercise_photos/x.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, "${context.packageName}.workout-shares", outside) }
        assertNotEquals(Uri.EMPTY, uri)
    }
}
