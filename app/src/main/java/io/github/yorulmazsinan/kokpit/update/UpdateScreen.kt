package io.github.yorulmazsinan.kokpit.update

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.BuildConfig
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.launch
import io.github.yorulmazsinan.kokpit.storage.removableRoots

@Composable
fun UpdateScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = container.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.update_title), onBack) {
        LazyColumn {
            item { ListRow(stringResource(R.string.update_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), stringResource(R.string.update_usb_help)) {} }
            item {
                val text = when (val s = state) {
                    UpdateState.Idle -> stringResource(R.string.update_idle)
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                    is UpdateState.Available -> stringResource(R.string.update_available, s.info.versionName)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, s.info.versionName, s.percent)
                    is UpdateState.Ready -> stringResource(R.string.update_ready, s.info.versionName)
                    is UpdateState.Failed -> stringResource(R.string.update_failed, s.reason)
                }
                SectionHeader(text)
            }
            (state as? UpdateState.Downloading)?.let { d ->
                item { LinearProgressIndicator(progress = { d.percent / 100f }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
            val notes = when (val s = state) {
                is UpdateState.Available -> s.info.notes
                is UpdateState.Downloading -> s.info.notes
                is UpdateState.Ready -> s.info.notes
                else -> ""
            }
            if (notes.isNotBlank()) item { HelpText(notes) }
            item {
                val s = state
                val (label, action) = when (s) {
                    is UpdateState.Available -> stringResource(R.string.update_download) to { scope.launch { updater.download(s.info) }; Unit }
                    is UpdateState.Ready -> stringResource(R.string.update_install) to {
                        if (updater.canInstall()) safeStart(context, updater.installIntent(s.file)) else safeStart(context, updater.allowInstallIntent())
                        Unit
                    }
                    else -> stringResource(R.string.update_check_usb) to {
                        scope.launch {
                            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { updater.checkUsb(removableRoots(context)) }
                            if (found == null) android.widget.Toast.makeText(context, R.string.update_usb_none, android.widget.Toast.LENGTH_LONG).show()
                        }
                        Unit
                    }
                }
                Button(
                    onClick = action,
                    enabled = s !is UpdateState.Checking && s !is UpdateState.Downloading,
                    shape = KokpitButtonShape,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = Dimens.MinTouch),
                ) { Text(label) }
            }
            if (state is UpdateState.Ready && !updater.canInstall()) item { HelpText(stringResource(R.string.update_allow_help)) }
            item { SectionHeader(stringResource(R.string.update_site_section)) }
            item {
                ListRow(stringResource(R.string.update_check), UPDATE_BASE_URL) { scope.launch { updater.check() } }
            }
            item {
                ToggleRow(stringResource(R.string.update_auto), settings.autoUpdate, stringResource(R.string.update_auto_help)) { on ->
                    scope.launch { container.settings.update { it.copy(autoUpdate = on) } }
                }
            }
            item { HelpText(stringResource(R.string.update_safety)) }
        }
    }
}
