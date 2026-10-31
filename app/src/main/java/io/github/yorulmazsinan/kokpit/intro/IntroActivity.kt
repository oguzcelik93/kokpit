package io.github.yorulmazsinan.kokpit.intro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.yorulmazsinan.kokpit.KokpitApp
import io.github.yorulmazsinan.kokpit.MainActivity

/** Full-screen intro video; any touch, the end of the clip or any error returns to the home screen. */
class IntroActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val id = intent.getStringExtra(EXTRA_ID)
        // Built-in clips play straight from the APK; the user's own from app storage.
        val uri = id?.let { builtinUri(this, it) ?: (application as KokpitApp).container.introLibrary.fileFor(it)?.let(Uri::fromFile) }
        if (uri == null) {
            finish()
            return
        }
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        exo.volume = if (intent.getBooleanExtra(EXTRA_SOUND, false)) 1f else 0f
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finish()
            }

            override fun onPlayerError(error: PlaybackException) = finish()
        })
        val view = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShutterBackgroundColor(Color.BLACK)
            this.player = exo
            setOnTouchListener { _, _ -> finish(); true }
        }
        setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.postDelayed({ if (!isFinishing) finish() }, MAX_PLAY_MS)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.play()
    }

    override fun finish() {
        // Spec §7b: the clip hands over to the home screen, not to whatever ran before sleep.
        if (intent.getBooleanExtra(EXTRA_RETURN_HOME, false)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "video_id"
        private const val EXTRA_SOUND = "with_sound"
        private const val EXTRA_RETURN_HOME = "return_home"
        private const val MAX_PLAY_MS = 15_000L

        fun intent(context: Context, videoId: String, withSound: Boolean, returnHome: Boolean = false): Intent =
            Intent(context, IntroActivity::class.java)
                .putExtra(EXTRA_ID, videoId)
                .putExtra(EXTRA_SOUND, withSound)
                .putExtra(EXTRA_RETURN_HOME, returnHome)
    }
}
