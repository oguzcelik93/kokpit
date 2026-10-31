package io.github.yorulmazsinan.kokpit.player

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibraryTest {
    @Test
    fun findsMusicAndVideoSkippingHiddenAndAndroidFolders() {
        val root = createTempDirectory("usb").toFile()
        fun touch(path: String) = File(root, path).apply { parentFile?.mkdirs(); writeText("x") }
        touch("Muzik/Barış Manço - Gesi Bağları.MP3")
        touch("Muzik/kapak.jpg")
        touch("Videolar/konser.mkv")
        touch(".Trash/silinen.mp3")
        touch("Android/data/x/gizli.mp3")
        val found = findMedia(listOf(root, root))
        assertEquals(listOf("Barış Manço - Gesi Bağları", "konser"), found.map { it.name })
        assertEquals(listOf(MediaKind.AUDIO, MediaKind.VIDEO), found.map { it.kind })
        assertEquals("Muzik", found.first().folder)
    }

    @Test
    fun movingHasHysteresis() {
        assertEquals(true, stillMoving(false, 12))
        assertEquals(true, stillMoving(true, 7))
        assertEquals(false, stillMoving(false, 7))
        assertEquals(false, stillMoving(true, 3))
        assertEquals("no GPS keeps the last answer", true, stillMoving(true, null))
    }
}
