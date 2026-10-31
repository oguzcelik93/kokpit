package io.github.yorulmazsinan.kokpit.music

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.media.NowPlaying
import io.github.yorulmazsinan.kokpit.media.NowPlayingState
import io.github.yorulmazsinan.kokpit.ui.components.HeaderClock
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.delay

@Composable
fun BtMusicScreen(
    state: NowPlayingState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    ScreenScaffold(stringResource(R.string.bt_music_title), onBack, actions = { HeaderClock() }) {
        Row(
            Modifier.fillMaxSize().padding(start = Dimens.ScreenPadding * 2, end = Dimens.ScreenPadding * 2, bottom = Dimens.ScreenPadding * 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            val np = (state as? NowPlayingState.Playing)?.nowPlaying
            Box(
                Modifier.fillMaxHeight(0.85f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (np?.art != null) {
                    Image(np.art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                when (state) {
                    NowPlayingState.AccessMissing -> Text(
                        stringResource(R.string.media_access),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onGrantAccess),
                    )
                    NowPlayingState.Idle -> Text(stringResource(R.string.nothing_playing), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is NowPlayingState.Playing -> TrackInfo(state.nowPlaying)
                }
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(Dimens.MinTouch)) {
                        Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.media_previous), modifier = Modifier.size(40.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).testTag("bt_play_pause"),
                    ) {
                        Icon(
                            if (np?.isPlaying == true) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            stringResource(R.string.media_play_pause),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(Dimens.MinTouch)) {
                        Icon(Icons.Outlined.SkipNext, stringResource(R.string.media_next), modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackInfo(np: NowPlaying) {
    val position by produceState(np.positionMs, np) {
        while (true) {
            value = if (np.isPlaying && np.positionAtElapsedMs > 0) {
                np.positionMs + (SystemClock.elapsedRealtime() - np.positionAtElapsedMs)
            } else {
                np.positionMs
            }
            delay(1000)
        }
    }
    Text(np.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(
        listOfNotNull(np.artist, np.sourceLabel).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    if (np.durationMs > 0) {
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { (position.toFloat() / np.durationMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMs(np.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
