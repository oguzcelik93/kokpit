package io.github.yorulmazsinan.kokpit.media

import android.graphics.Bitmap
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumArtTest {
    @Test
    fun largeArtIsCopiedDownToAThumbnail() {
        val big = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val art = safeAlbumArt(big)!!
        assertTrue(art.width <= 256 && art.height <= 256)
        assertTrue(art !== big)
    }

    @Test
    fun recycledArtIsDropped() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { recycle() }
        assertNull(safeAlbumArt(bitmap))
    }

    @Test
    fun nullStaysNull() = assertNull(safeAlbumArt(null))
}
