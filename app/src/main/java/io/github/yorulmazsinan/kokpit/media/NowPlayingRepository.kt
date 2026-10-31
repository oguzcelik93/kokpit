package io.github.yorulmazsinan.kokpit.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String?,
    val sourceLabel: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    /** `elapsedRealtime` when [positionMs] was measured, to extrapolate while playing. */
    val positionAtElapsedMs: Long = 0L,
)

sealed interface NowPlayingState {
    data object AccessMissing : NowPlayingState
    data object Idle : NowPlayingState
    data class Playing(val nowPlaying: NowPlaying) : NowPlayingState
}

class NowPlayingRepository(private val context: Context) {
    private val _state = MutableStateFlow<NowPlayingState>(NowPlayingState.Idle)
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var listening = false

    /** A truer playing state for a session's package (the head unit's, for Bluetooth); null keeps the session's own. */
    var playingOverride: (String) -> Boolean? = { null }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            log("state ${stateName(state?.state)}")
            publish()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list.orEmpty())
    }

    fun hasAccess(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Call at start and whenever notification access may have been granted. */
    fun refreshAccess() {
        if (!hasAccess()) {
            _state.value = NowPlayingState.AccessMissing
            return
        }
        if (!listening) {
            runCatching { manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler) }
                .onSuccess { listening = true }
        }
        watch(runCatching { manager.getActiveSessions(listenerComponent) }.getOrDefault(emptyList()))
    }

    /** The package of the session shown, if any. */
    fun currentPackage(): String? = current?.packageName

    /** Re-reads the sessions, e.g. after a play/pause the session itself may not report. */
    fun refresh() = handler.post { publish() }

    fun playPause() {
        val controller = current ?: return log("kokpit play/pause: no session")
        log("kokpit play/pause -> ${controller.packageName} (was ${stateName(controller.playbackState?.state)})")
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    /** Presses play on [packageName]'s session if it still has one. */
    fun play(packageName: String): Boolean {
        val controller = controllers.firstOrNull { it.packageName == packageName } ?: return false
        log("kokpit resume -> $packageName")
        controller.transportControls.play()
        return true
    }

    fun next() {
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        current?.transportControls?.skipToPrevious()
    }

    private fun log(event: String) = MediaEventLog.shared.add(android.os.SystemClock.elapsedRealtime(), event)

    private fun stateName(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_NONE, null -> "NONE"
        else -> "state$state"
    }

    private fun watch(list: List<MediaController>) {
        log("sessions: " + list.joinToString { "${it.packageName}=${stateName(it.playbackState?.state)}" })
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        list.forEach { it.registerCallback(callback, handler) }
        publish()
    }

    private fun publish() {
        // Callbacks arrive from other apps' sessions; one bad session must not take Kokpit down.
        runCatching { publishUnsafe() }.onFailure { _state.value = NowPlayingState.Idle }
    }

    private fun publishUnsafe() {
        val snapshots = controllers.map { controller ->
            val snapshot = controller.snapshot()
            controller to (playingOverride(snapshot.packageName)?.let { snapshot.copy(isPlaying = it) } ?: snapshot)
        }
        val chosen = selectSession(snapshots.map { it.second })
        current = snapshots.firstOrNull { it.second == chosen }?.first
        val controller = current
        _state.value = if (chosen == null || controller == null) {
            NowPlayingState.Idle
        } else {
            val metadata = controller.metadata
            NowPlayingState.Playing(
                NowPlaying(
                    title = chosen.title.orEmpty(),
                    artist = chosen.artist,
                    sourceLabel = appLabel(chosen.packageName),
                    art = safeAlbumArt(
                        runCatching {
                            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        }.getOrNull(),
                    ),
                    isPlaying = chosen.isPlaying,
                    durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                    positionMs = controller.playbackState?.position ?: 0L,
                    positionAtElapsedMs = controller.playbackState?.lastPositionUpdateTime ?: 0L,
                ),
            )
        }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(packageName)
}

private fun MediaController.snapshot() = SessionSnapshot(
    packageName = packageName,
    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
    isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
    lastActiveMs = playbackState?.lastPositionUpdateTime ?: 0L,
)
