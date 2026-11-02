package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import io.github.yorulmazsinan.kokpit.carplay.isIphoneAttached
import io.github.yorulmazsinan.kokpit.headunit.BtMusic
import io.github.yorulmazsinan.kokpit.headunit.VendorModes
import io.github.yorulmazsinan.kokpit.headunit.VendorSource
import io.github.yorulmazsinan.kokpit.media.NowPlayingRepository
import io.github.yorulmazsinan.kokpit.media.NowPlayingState

/** What was playing: `radio`, `bt`, `app:<package>` or `none`. */
sealed interface LastSource {
    data object None : LastSource
    data object Radio : LastSource
    data object Bluetooth : LastSource
    data class App(val packageName: String) : LastSource

    fun encode(): String = when (this) {
        None -> "none"
        Radio -> "radio"
        Bluetooth -> "bt"
        is App -> "app:$packageName"
    }

    companion object {
        fun decode(value: String?): LastSource = when {
            value == "radio" -> Radio
            value == "bt" -> Bluetooth
            value != null && value.startsWith("app:") && value.length > 4 -> App(value.substring(4))
            else -> None
        }
    }
}

/**
 * Remembers the playing source while driving and starts it again when the ignition comes
 * back on. Music the driver paused before stopping stays paused. Skipped while an iPhone is
 * plugged in: CarPlay brings its own audio.
 */
class ResumePlayback(
    private val context: Context,
    private val nowPlaying: NowPlayingRepository,
    private val modes: VendorModes,
    private val btMusic: BtMusic,
) {
    private val prefs = context.applicationContext.getSharedPreferences("resume", Context.MODE_PRIVATE)

    val last: LastSource get() = LastSource.decode(prefs.getString(KEY, null))

    /** Driver profiles: each phone's driver resumes their own music. */
    fun restore(source: LastSource) {
        prefs.edit().putString(KEY, source.encode()).apply()
    }

    /** Blocking (vendor binder calls): call off the main thread, while awake. */
    fun sample() {
        val playing = nowPlaying.state.value as? NowPlayingState.Playing
        val source = when {
            runCatching { modes.isActive(VendorSource.RADIO) }.getOrDefault(false) -> LastSource.Radio
            runCatching { modes.isActive(VendorSource.BT_MUSIC) }.getOrDefault(false) && btMusic.isPlaying() != false -> LastSource.Bluetooth
            playing?.nowPlaying?.isPlaying == true -> nowPlaying.currentPackage()?.let { LastSource.App(it) } ?: LastSource.None
            else -> LastSource.None
        }
        prefs.edit().putString(KEY, source.encode()).apply()
    }

    /** Blocking: call off the main thread a few seconds after wake. */
    fun resume(): LastSource {
        if (isIphoneAttached(context)) return LastSource.None
        val source = last
        when (source) {
            LastSource.None -> Unit
            LastSource.Radio -> modes.enter(VendorSource.RADIO)
            LastSource.Bluetooth -> if (modes.enter(VendorSource.BT_MUSIC) && btMusic.isPlaying() == false) btMusic.toggle()
            is LastSource.App -> if (!nowPlaying.play(source.packageName)) pressPlay()
        }
        return source
    }

    /** Android hands a media key to the last app that played, even after its process ended. */
    private fun pressPlay() {
        val audio = context.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }

    private companion object {
        const val KEY = "last_source"
    }
}
