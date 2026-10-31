package io.github.yorulmazsinan.kokpit.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(val current: MediaFile? = null, val isPlaying: Boolean = false, val hasNext: Boolean = false)

/**
 * Kokpit's own player for USB music and video. It lives with the app, so music keeps playing
 * on other screens, and it publishes a media session so the home card can control it.
 * Main thread only.
 */
class UsbPlayer(private val context: Context) {
    private var exo: ExoPlayer? = null
    private var session: MediaSession? = null
    private var queue: List<MediaFile> = emptyList()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val player: ExoPlayer get() = exo ?: build().also { exo = it }

    private fun build(): ExoPlayer {
        val audio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        val built = ExoPlayer.Builder(context).setAudioAttributes(audio, true).setHandleAudioBecomingNoisy(true).build()
        built.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()
        })
        session = runCatching { MediaSession.Builder(context, built).setId("kokpit-usb").build() }.getOrNull()
        return built
    }

    fun play(files: List<MediaFile>, index: Int) {
        queue = files
        val items = files.map { file ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(file.path)))
                .setMediaId(file.path)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).setArtist(file.folder).build())
                .build()
        }
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
    }

    fun playPause() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() = exo?.seekToNextMediaItem()

    fun previous() = exo?.seekToPreviousMediaItem()

    private fun publish() {
        val p = exo ?: return
        _state.value = PlayerState(queue.getOrNull(p.currentMediaItemIndex), p.isPlaying, p.hasNextMediaItem())
    }
}
