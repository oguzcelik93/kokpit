package io.github.yorulmazsinan.kokpit.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(root: File, path: String) = File(root, path).apply { parentFile?.mkdirs(); writeText("x") }

    @Test
    fun findsMp4FilesInSubfoldersCaseInsensitive() {
        val usb = tmp.newFolder("usb")
        touch(usb, "Kokpit/Videolar/a.mp4")
        touch(usb, "Kokpit/Videolar/B.MP4")
        touch(usb, "Kokpit/notes.txt")
        touch(usb, "clip.mov")
        val found = findVideos(listOf(usb)).map { it.name }
        assertEquals(listOf("B.MP4", "a.mp4").sorted(), found.sorted())
    }

    @Test
    fun skipsHiddenAndAndroidFoldersAndRespectsDepth() {
        val usb = tmp.newFolder("usb")
        touch(usb, ".Trashes/old.mp4")
        touch(usb, "Android/data/x/files/cache.mp4")
        touch(usb, "a/b/c/d/e/deep.mp4")
        touch(usb, "a/shallow.mp4")
        assertEquals(listOf("shallow.mp4"), findVideos(listOf(usb), maxDepth = 4).map { it.name })
    }

    @Test
    fun ignoresMissingRootsAndDuplicates() {
        val usb = tmp.newFolder("usb")
        touch(usb, "v.mp4")
        val found = findVideos(listOf(usb, usb, File(tmp.root, "nope")))
        assertEquals(1, found.size)
    }

    @Test
    fun volumeRootStripsAppSpecificSuffix() {
        assertEquals(File("/storage/1A2B-3C4D"), volumeRoot(File("/storage/1A2B-3C4D/Android/data/io.github.yorulmazsinan.kokpit/files")))
        assertNull(volumeRoot(File("/data/user/0/io.github.yorulmazsinan.kokpit/files")))
    }

    @Test
    fun writeToDirsReportsOnlySuccesses() {
        val ok = tmp.newFolder("ok")
        val notADir = touch(tmp.root, "file-not-dir")
        val written = writeToDirs(listOf(ok, notADir), "kokpit-diagnostics.txt", "hello")
        assertEquals(listOf(File(ok, "kokpit-diagnostics.txt")), written)
        assertTrue(File(ok, "kokpit-diagnostics.txt").readText() == "hello")
    }

    @Test
    fun exportDirsPreferUsbRootThenAppFolderThenInternal() {
        val usb = tmp.newFolder("usb")
        val usbApp = tmp.newFolder("usb-app")
        val internal = tmp.newFolder("sdcard")
        val dirs = exportDirs(roots = listOf(usb, usb), appDirs = listOf(usbApp), internal = internal)
        assertEquals(listOf(File(usb, "Kokpit"), usbApp, File(internal, "Kokpit")), dirs)
    }

    @Test
    fun writeFirstStopsAtTheFirstWritableDir() {
        val notADir = touch(tmp.root, "blocked")
        val ok = tmp.newFolder("ok")
        val other = tmp.newFolder("other")
        val written = writeFirst(listOf(File(notADir, "x"), ok, other), "a.txt", "hi")
        assertEquals(File(ok, "a.txt"), written)
        assertTrue(!File(other, "a.txt").exists())
    }
}
