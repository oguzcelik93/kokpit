package io.github.yorulmazsinan.kokpit.car

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WallpaperStoreTest {
    private fun jpeg(w: Int, h: Int): ByteArrayInputStream {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun waitFor(check: () -> Boolean) {
        repeat(100) { if (check()) return; Thread.sleep(20) }
    }

    @Test
    fun albumKeepsPhotosAndKnowsPortraits() {
        val store = WallpaperStore(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.IO))
        Thread.sleep(200)
        assertTrue(store.save(jpeg(900, 1600)))
        assertTrue(store.image.value!!.portrait)
        Thread.sleep(5) // distinct ids
        assertTrue(store.save(jpeg(1600, 900)))
        assertFalse(store.image.value!!.portrait)
        assertEquals(2, store.photos.value.size)
        val newest = store.photos.value.first()
        assertEquals(newest, store.selected.value)

        val older = store.photos.value.last()
        store.select(older)
        waitFor { store.selected.value == older }
        assertTrue(store.image.value!!.portrait)

        store.delete(older)
        waitFor { store.photos.value.size == 1 }
        waitFor { store.selected.value == newest }
        assertEquals(newest, store.selected.value)
        assertFalse(store.save(ByteArrayInputStream("not an image".toByteArray())))
    }

    @Test
    fun bigPhotosAreShrunk() {
        val store = WallpaperStore(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.IO))
        Thread.sleep(200)
        assertTrue(store.save(jpeg(3000, 4000)))
        val img = store.image.value!!.photo
        assertTrue(maxOf(img.width, img.height) <= WALLPAPER_MAX_SIDE)
    }

    @Test
    fun signatures() {
        assertTrue(looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertTrue(looksLikeImage(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
        assertTrue(looksLikeImage("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()))
        assertTrue(looksLikeImage("\u0000\u0000\u0000\u0018ftypheic".toByteArray()))
        assertFalse(looksLikeImage("<html>hello".toByteArray()))
        assertFalse(looksLikeImage(ByteArray(0)))
    }

    @Test
    fun portraitTest() {
        assertTrue(isPortrait(900, 1600))
        assertFalse(isPortrait(1600, 900))
        assertFalse(isPortrait(1000, 1020)) // near square fills the card
    }
}
