package io.github.yorulmazsinan.kokpit.headunit

import kotlinx.coroutines.delay

/** Bluetooth music shows up as this package's media session. */
const val BLUETOOTH_SESSION_PACKAGE = "com.android.bluetooth"

/** The head unit's AVRCP play status (0 stopped, 1 playing, 2 paused); null when unknown. */
fun avrcpPlaying(status: Int?): Boolean? = status?.let { it == 1 }

/** Bluetooth music (or nothing at all) is driven through the head unit, everything else through its session. */
fun usesHeadUnitMusic(sessionPackage: String?): Boolean =
    sessionPackage == null || sessionPackage == BLUETOOTH_SESSION_PACKAGE

/**
 * Bluetooth music through the head unit. The phone's Bluetooth media session on this unit keeps
 * reporting PLAYING after the music stops, so play/pause asks the head unit what is really on.
 * These are blocking binder calls.
 */
class BtMusic(private val phone: () -> HeadUnitPhone?, private val enterSource: () -> Boolean = { true }) {
    fun isPlaying(): Boolean? = avrcpPlaying(phone()?.musicState())

    /** False when the head unit can't tell, so the caller falls back to the media session. */
    fun toggle(): Boolean {
        val unit = phone() ?: return false
        val playing = avrcpPlaying(unit.musicState()) ?: return false
        if (playing) return unit.pause()
        // Starting music while another source is on makes the vendor jump to its own music screen.
        enterSource()
        return unit.play()
    }

    private var playingBeforeSwitch = false

    fun rememberBeforeSwitch() {
        playingBeforeSwitch = isPlaying() == true
    }

    /**
     * Switching the source to Bluetooth takes the audio focus for a moment, which pauses the phone.
     * Start it again only if it was playing: a bare "play" makes an idle iPhone shuffle the library.
     */
    suspend fun resumeAfterSwitch() {
        if (!playingBeforeSwitch) return
        repeat(2) {
            delay(1_500)
            val unit = phone() ?: return
            if (avrcpPlaying(unit.musicState()) != false) return
            unit.play()
        }
    }
}
