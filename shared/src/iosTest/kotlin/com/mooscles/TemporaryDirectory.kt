package com.mooscles

import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import kotlin.uuid.Uuid

/**
 * A fresh, not yet created directory under the simulator sandbox's tmp for one test, so the
 * openers have to create it themselves. [delete] removes it and everything in it.
 */
class TemporaryDirectory {
    val path: String = NSTemporaryDirectory().trimEnd('/') + "/mooscles-test-" + Uuid.random()

    fun exists(relative: String): Boolean = FileSystem.SYSTEM.exists("$path/$relative".toPath())

    fun delete() = FileSystem.SYSTEM.deleteRecursively(path.toPath(), mustExist = false)
}
