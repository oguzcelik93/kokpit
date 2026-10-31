package io.github.yorulmazsinan.kokpit.intro

import android.Manifest
import android.content.Context
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.yorulmazsinan.kokpit.storage.findVideos
import io.github.yorulmazsinan.kokpit.storage.removableRoots
import java.io.File
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.SettingsPicker
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IntroSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val intro = settings.intro
    var videos by remember { mutableStateOf(allIntroVideos(container.introLibrary)) }
    fun update(transform: (IntroSettings) -> IntroSettings) {
        scope.launch { container.settings.update { it.copy(intro = transform(it.intro)) } }
    }

    var usbVideos by remember { mutableStateOf<List<File>?>(null) }
    fun importFiles(files: List<File>) {
        scope.launch {
            val errors = withContext(Dispatchers.IO) {
                files.count { f -> container.introLibrary.import(f.inputStream(), f.name, f.length()).isFailure }
            }
            videos = allIntroVideos(container.introLibrary)
            if (errors > 0) Toast.makeText(context, R.string.intro_import_failed, Toast.LENGTH_LONG).show()
            if (videos.isNotEmpty() && intro.mode == IntroMode.OFF) update { it.copy(mode = IntroMode.SELECTED) }
        }
    }
    fun scanUsb() {
        scope.launch {
            usbVideos = withContext(Dispatchers.IO) { findVideos(removableRoots(context)) }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { scanUsb() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val errors = withContext(Dispatchers.IO) { uris.count { !importUri(context, container.introLibrary, it) } }
            videos = allIntroVideos(container.introLibrary)
            if (errors > 0) Toast.makeText(context, R.string.intro_import_failed, Toast.LENGTH_LONG).show()
            if (videos.isNotEmpty() && intro.mode == IntroMode.OFF) update { it.copy(mode = IntroMode.SELECTED) }
        }
    }

    usbVideos?.let { found ->
        UsbVideoDialog(found, onDismiss = { usbVideos = null }) { chosen ->
            usbVideos = null
            importFiles(chosen)
        }
    }

    ScreenScaffold(stringResource(R.string.settings_intro), onBack) {
        LazyColumn {
            item {
                val modes = listOf(
                    IntroMode.OFF to R.string.intro_mode_off,
                    IntroMode.SEASONAL to R.string.intro_mode_seasonal,
                    IntroMode.SELECTED to R.string.intro_mode_selected,
                    IntroMode.SEQUENTIAL to R.string.intro_mode_sequential,
                    IntroMode.RANDOM to R.string.intro_mode_random,
                )
                SettingsPicker(modes, modes.first { it.first == intro.mode }, { (mode, _) -> update { it.copy(mode = mode) } }) { stringResource(it.second) }
            }
            if (intro.mode == IntroMode.SEASONAL) item { HelpText(stringResource(R.string.intro_mode_seasonal_help)) }
            item { ToggleRow(stringResource(R.string.intro_every_wake), intro.everyWake, stringResource(R.string.intro_every_wake_help)) { on -> update { it.copy(everyWake = on) } } }
            item { ToggleRow(stringResource(R.string.intro_with_sound), intro.withSound) { on -> update { it.copy(withSound = on) } } }
            item {
                Button(
                    shape = KokpitButtonShape,
                    onClick = { storagePermission.launch(readVideoPermission()) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = Dimens.MinTouch),
                ) { Text(stringResource(R.string.intro_add)) }
            }
            item { HelpText(stringResource(R.string.intro_add_help)) }
            item {
                TextButton(
                    onClick = {
                        runCatching { picker.launch(arrayOf("video/mp4")) }
                            .onFailure { Toast.makeText(context, R.string.start_failed, Toast.LENGTH_SHORT).show() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = Dimens.MinTouch),
                ) { Text(stringResource(R.string.intro_add_other)) }
            }
            item { SectionHeader(stringResource(R.string.intro_library)) }
            items(videos, key = { it.id }) { video ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        val selected = intro.mode == IntroMode.SELECTED && (intro.selectedId ?: videos.first().id) == video.id
                        Text(
                            video.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                        val detail = if (isBuiltin(video.id)) stringResource(R.string.intro_builtin) else String.format(Locale.getDefault(), "%.1f MB", video.sizeBytes / 1_048_576.0)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { update { it.copy(mode = IntroMode.SELECTED, selectedId = video.id) } }, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                        Text(stringResource(R.string.intro_use_this))
                    }
                    TextButton(onClick = { context.startActivity(IntroActivity.intent(context, video.id, intro.withSound)) }, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                        Text(stringResource(R.string.intro_preview))
                    }
                    if (!isBuiltin(video.id)) {
                        TextButton(
                            onClick = {
                                container.introLibrary.delete(video.id)
                                videos = allIntroVideos(container.introLibrary)
                            },
                            modifier = Modifier.heightIn(min = Dimens.MinTouch),
                        ) { Text(stringResource(R.string.intro_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun importUri(context: Context, library: IntroVideoLibrary, uri: Uri): Boolean {
    var name = uri.lastPathSegment ?: "video.mp4"
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            name = c.getString(0) ?: name
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return false
    return library.import(input, name, size).isSuccess
}

private fun readVideoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
