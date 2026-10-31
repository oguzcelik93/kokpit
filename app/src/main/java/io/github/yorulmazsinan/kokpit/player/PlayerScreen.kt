package io.github.yorulmazsinan.kokpit.player

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.home.cards.elapsedText
import io.github.yorulmazsinan.kokpit.storage.removableRoots
import io.github.yorulmazsinan.kokpit.ui.components.SegmentedPicker
import io.github.yorulmazsinan.kokpit.ui.components.HeaderClock
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val usb = container.usbPlayer
    val state by usb.state.collectAsStateWithLifecycle()
    val trip by container.drive.state.collectAsStateWithLifecycle()
    var files by remember { mutableStateOf<List<MediaFile>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var moving by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
    LaunchedEffect(reload) {
        files = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            findMedia(removableRoots(context) + Environment.getExternalStorageDirectory())
        }
    }
    LaunchedEffect(trip.speedKmh) { moving = stillMoving(moving, trip.speedKmh) }
    LaunchedEffect(Unit) {
        while (true) {
            position = usb.player.currentPosition
            duration = usb.player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    PlayerContent(
        files = files,
        state = state,
        moving = moving,
        positionMs = position,
        durationMs = duration,
        onPlay = { list, index -> usb.play(list, index) },
        onPlayPause = usb::playPause,
        onNext = { usb.next() },
        onPrevious = { usb.previous() },
        onBack = onBack,
        video = {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        player = usb.player
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
fun PlayerContent(
    files: List<MediaFile>?,
    state: PlayerState,
    moving: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlay: (List<MediaFile>, Int) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBack: () -> Unit,
    video: @Composable () -> Unit = {},
) {
    var kind by remember { mutableStateOf(MediaKind.AUDIO) }
    ScreenScaffold(stringResource(R.string.player_title), onBack, actions = { HeaderClock() }) {
        Row(
            Modifier.fillMaxSize().padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            PlayerCard(Modifier.weight(1f).fillMaxHeight()) {
                Column {
                    val kinds = listOf(MediaKind.AUDIO to R.string.player_music, MediaKind.VIDEO to R.string.player_video)
                    SegmentedPicker(kinds, kinds.first { it.first == kind }, { kind = it.first }, { stringResource(it.second) })
                    Spacer(Modifier.size(8.dp))
                    val shown = files.orEmpty().filter { it.kind == kind }
                    when {
                        files == null -> Message(stringResource(R.string.player_scanning))
                        shown.isEmpty() -> Message(stringResource(R.string.player_empty))
                        else -> LazyColumn(Modifier.testTag("player_list")) {
                            itemsIndexed(shown, key = { _, f -> f.path }) { i, file ->
                                FileRow(file, file.path == state.current?.path) { onPlay(shown, i) }
                            }
                        }
                    }
                }
            }
            PlayerCard(Modifier.weight(1.25f).fillMaxHeight()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val current = state.current
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            current == null -> Icon(Icons.Outlined.Usb, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                            current.kind == MediaKind.AUDIO -> Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                            // While driving the picture stays off; the sound carries on.
                            moving -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).testTag("player_moving")) {
                                Icon(Icons.Outlined.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.player_moving), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            }
                            else -> video()
                        }
                    }
                    Text(
                        current?.name ?: stringResource(R.string.player_pick),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current != null) {
                        LinearProgressIndicator(
                            progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(elapsedText(positionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text(elapsedText(durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrevious, enabled = current != null, modifier = Modifier.size(Dimens.MinTouch)) {
                            Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.media_previous), modifier = Modifier.size(36.dp))
                        }
                        Surface(
                            onClick = onPlayPause,
                            enabled = current != null,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(68.dp).testTag("player_play"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    stringResource(R.string.media_play_pause),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                        IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(Dimens.MinTouch)) {
                            Icon(Icons.Outlined.SkipNext, stringResource(R.string.media_next), modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(Dimens.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Box(Modifier.padding(Dimens.CardPadding)) { content() } }
}

@Composable
private fun FileRow(file: MediaFile, playing: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch).clickable(onClick = onClick).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (file.kind == MediaKind.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.MusicNote,
                null,
                tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(file.folder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
}
