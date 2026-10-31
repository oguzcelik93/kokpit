package io.github.yorulmazsinan.kokpit.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IntroVideoLibraryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val library by lazy { IntroVideoLibrary(tmp.newFolder("intro")) }

    @Test
    fun importCopiesAndLists() {
        val bytes = ByteArray(1024) { it.toByte() }
        val video = library.import(bytes.inputStream(), "Captiva gece.mp4", bytes.size.toLong()).getOrThrow()
        assertEquals("Captiva gece.mp4", video.displayName)
        assertEquals(listOf(video), library.list())
        assertTrue(library.fileFor(video.id)!!.readBytes().contentEquals(bytes))
    }

    @Test
    fun rejectsOver20Mb() {
        val result = library.import(ByteArray(0).inputStream(), "big.mp4", 21L * 1024 * 1024)
        assertTrue(result.exceptionOrNull() is IntroImportError.TooLarge)
        assertTrue(library.list().isEmpty())
    }

    @Test
    fun rejectsStreamThatExceedsLimitDespiteDeclaredSize() {
        val result = library.import(ByteArray(21 * 1024 * 1024).inputStream(), "liar.mp4", sizeBytes = -1)
        assertTrue(result.exceptionOrNull() is IntroImportError.TooLarge)
        assertTrue(library.list().isEmpty())
    }

    @Test
    fun rejectsNonMp4() {
        val result = library.import(ByteArray(10).inputStream(), "clip.mov", 10)
        assertTrue(result.exceptionOrNull() is IntroImportError.UnsupportedType)
    }

    @Test
    fun deleteRemovesFile() {
        val video = library.import(ByteArray(10).inputStream(), "a.mp4", 10).getOrThrow()
        val file = library.fileFor(video.id)!!
        library.delete(video.id)
        assertFalse(file.exists())
        assertTrue(library.list().isEmpty())
    }
}
