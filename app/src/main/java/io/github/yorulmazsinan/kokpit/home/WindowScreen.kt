package io.github.yorulmazsinan.kokpit.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import io.github.yorulmazsinan.kokpit.car.gesture
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.car.PhotoReceiver
import io.github.yorulmazsinan.kokpit.car.QrCode
import io.github.yorulmazsinan.kokpit.car.findImages
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BRAVE = "com.brave.browser"

/** Settings of the home screen's window card: which app it opens, and its photo. */
@Composable
fun WindowScreen(container: AppContainer, onBack: () -> Unit, onPickApp: (DockSlot) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dock by container.dock.targets.collectAsStateWithLifecycle()
    val image by container.wallpaper.image.collectAsStateWithLifecycle()
    val photos by container.wallpaper.photos.collectAsStateWithLifecycle()
    val selected by container.wallpaper.selected.collectAsStateWithLifecycle()
    val target = dock[DockSlot.BROWSER]
    var link by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<File>?>(null) }
    var receiver by remember { mutableStateOf<PhotoReceiver?>(null) }
    var framing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { receiver?.stop() } }

    ScreenScaffold(stringResource(R.string.window_title), onBack) {
        LazyColumn {
            item { SectionHeader(stringResource(R.string.window_app)) }
            item {
                ListRow(target?.let(container.installedApps::label) ?: stringResource(R.string.settings_not_set), stringResource(R.string.window_app_help)) {
                    onPickApp(DockSlot.BROWSER)
                }
            }
            if (BRAVE !in container.installedApps.packages()) {
                item {
                    ListRow(stringResource(R.string.window_get_brave), stringResource(R.string.window_get_brave_help)) {
                        safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$BRAVE")))
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.window_photo)) }
            item {
                ListRow(stringResource(R.string.window_from_phone), stringResource(R.string.window_from_phone_help)) {
                    receiver?.stop()
                    val r = PhotoReceiver { input -> container.wallpaper.save(input) }
                    receiver = r
                    status = null
                    link = r.start(
                        onReceived = { scope.launch { status = context.getString(R.string.window_received); link = null } },
                        onStopped = { scope.launch { if (link != null) { link = null; status = context.getString(R.string.window_receiver_stopped) } } },
                    )
                    if (link == null) status = context.getString(R.string.window_no_network)
                }
            }
            link?.let { url ->
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrCode(url, Modifier.size(220.dp))
                        Column {
                            Text(stringResource(R.string.window_scan), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            status?.let { item { HelpText(it) } }
            item {
                ListRow(stringResource(R.string.window_from_storage), stringResource(R.string.window_from_storage_help)) {
                    scope.launch { found = withContext(Dispatchers.IO) { findImages(container.wallpaper.searchRoots()) } }
                }
            }
            found?.let { files ->
                if (files.isEmpty()) item { HelpText(stringResource(R.string.window_none_found)) }
                items(files, key = { it.absolutePath }) { file ->
                    ListRow(file.name, file.parentFile?.name.orEmpty()) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { container.wallpaper.save(file.inputStream()) }.getOrDefault(false) }
                            Toast.makeText(context, if (ok) R.string.window_set else R.string.window_not_image, Toast.LENGTH_SHORT).show()
                            if (ok) found = null
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.window_album, photos.size)) }
            if (photos.isEmpty()) {
                item { HelpText(stringResource(R.string.window_album_empty)) }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(photos, key = { it }) { id -> AlbumThumb(container, id, id == selected) { container.wallpaper.select(id) } }
                    }
                }
                item { HelpText(stringResource(R.string.window_album_help)) }
            }
            if (image != null) {
                item { ListRow("Kadrajı ayarla", "İki parmakla yakınlaştırın, sürükleyerek kaydırın: kartta tam bu kısım görünür") { framing = true } }
                item {
                    ListRow(stringResource(R.string.window_rotate), stringResource(R.string.window_rotate_help)) {
                        scope.launch(Dispatchers.IO) { container.wallpaper.rotateQuarter() }
                    }
                }
                item { ListRow(stringResource(R.string.window_remove), stringResource(R.string.window_remove_help)) { selected?.let(container.wallpaper::delete) } }
            }
        }
    }
    val img = image
    val id = selected
    if (framing && img != null && id != null) FramingEditor(container, id, img) { framing = false }
}

/**
 * "Kadrajı ayarla": the selected photo in a frame shaped like its home card; pinch to zoom,
 * drag to move (or the buttons, for one-finger screens). Saved per photo.
 */
@Composable
private fun FramingEditor(container: AppContainer, id: String, image: io.github.yorulmazsinan.kokpit.car.WallpaperImage, onClose: () -> Unit) {
    val saved by container.wallpaper.framing.collectAsStateWithLifecycle()
    val sizes by container.wallpaper.cardSizes.collectAsStateWithLifecycle()
    var f by remember(id) { mutableStateOf(saved[id] ?: io.github.yorulmazsinan.kokpit.car.Framing()) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val aspect = sizes[image.portrait]?.let { (w, h) -> w.toFloat() / h } ?: if (image.portrait) 0.62f else 1.9f
    val iw = image.photo.width.toFloat()
    val ih = image.photo.height.toFloat()
    fun step(zoom: Float, dx: Float, dy: Float) {
        if (box.width > 0) f = f.gesture(zoom, dx, dy, box.width.toFloat(), box.height.toFloat(), iw, ih)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val frameH = 420.dp
                Box(
                    Modifier.size(width = (frameH.value * aspect).coerceAtMost(620f).dp, height = (frameH.value * aspect).coerceAtMost(620f).dp / aspect)
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                        .onSizeChanged { box = it }
                        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> step(zoom, pan.x, pan.y) } },
                ) {
                    io.github.yorulmazsinan.kokpit.home.cards.FramedImage(image.photo, f, Modifier.fillMaxSize())
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kadraj", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = { step(1f / 1.15f, 0f, 0f) }) { Text("−") }
                        androidx.compose.material3.OutlinedButton(onClick = { step(1.15f, 0f, 0f) }) { Text("+") }
                    }
                    val nudge = 0.15f
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = { f = f.copy(fy = (f.fy - nudge).coerceIn(-1f, 1f)) }) { Text("↑") }
                        androidx.compose.material3.OutlinedButton(onClick = { f = f.copy(fy = (f.fy + nudge).coerceIn(-1f, 1f)) }) { Text("↓") }
                        androidx.compose.material3.OutlinedButton(onClick = { f = f.copy(fx = (f.fx - nudge).coerceIn(-1f, 1f)) }) { Text("←") }
                        androidx.compose.material3.OutlinedButton(onClick = { f = f.copy(fx = (f.fx + nudge).coerceIn(-1f, 1f)) }) { Text("→") }
                    }
                    androidx.compose.material3.Button(onClick = { container.wallpaper.setFraming(id, f); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Kaydet") }
                    androidx.compose.material3.OutlinedButton(onClick = { f = io.github.yorulmazsinan.kokpit.car.Framing() }, modifier = Modifier.fillMaxWidth()) { Text("Sıfırla") }
                    androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("İptal") }
                }
            }
        }
    }
}

@Composable
private fun AlbumThumb(container: AppContainer, id: String, selected: Boolean, onClick: () -> Unit) {
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, id, container.wallpaper.photos.collectAsStateWithLifecycle().value) {
        value = withContext(Dispatchers.IO) { container.wallpaper.thumbnail(id) }
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(width = 132.dp, height = 96.dp)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(Color.Black)
            .clickable(onClick = onClick),
    ) {
        thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}
