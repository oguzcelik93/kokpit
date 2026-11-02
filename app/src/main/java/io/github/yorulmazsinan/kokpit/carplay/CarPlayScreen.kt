package io.github.yorulmazsinan.kokpit.carplay

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.system.exportedActivities
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** CarPlay connection centre: which engine, iPhone status, auto-start and splash skipping. */
@Composable
fun CarPlayScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val dock by container.dock.targets.collectAsStateWithLifecycle()
    val installed = remember { container.installedApps.packages() }
    val engines = remember(installed) { installedEngines(installed) }
    val target = dock[DockSlot.CARPLAY]
    val current = engineFor(target, installed)
    var iphone by remember { mutableStateOf(isIphoneAttached(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            iphone = isIphoneAttached(context)
            delay(1_500)
        }
    }
    fun setTarget(value: String) = scope.launch { container.dock.setOverride(DockSlot.CARPLAY, value) }

    ScreenScaffold(stringResource(R.string.carplay_title), onBack) {
        LazyColumn {
            item {
                ListRow(
                    stringResource(if (iphone) R.string.carplay_iphone_on else R.string.carplay_iphone_off),
                    current?.let { stringResource(R.string.carplay_engine_current, it.name) } ?: stringResource(R.string.carplay_no_engine),
                ) {
                    target?.let(container.installedApps::launchIntent)?.let { safeStart(context, it) }
                }
            }
            item {
                ToggleRow(stringResource(R.string.settings_carplay_usb), settings.autoCarPlayOnUsb, stringResource(R.string.settings_carplay_usb_help)) { on ->
                    scope.launch { container.settings.update { it.copy(autoCarPlayOnUsb = on) } }
                }
            }
            item { SectionHeader(stringResource(R.string.carplay_engines)) }
            if (engines.isEmpty()) item { HelpText(stringResource(R.string.carplay_none_installed)) }
            items(engines, key = { it.target }) { engine ->
                val selected = engine.packageName == current?.packageName
                ListRow((if (selected) "● " else "") + engine.name, stringResource(kindHelp(engine.kind))) { setTarget(engine.target) }
            }
            item { HelpText(stringResource(R.string.carplay_direct_help)) }
            item { HelpText(stringResource(R.string.projection_hotspot_help)) }
            current?.let { engine ->
                val screens = exportedActivities(context.packageManager, engine.packageName)
                if (screens.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.carplay_skip_splash)) }
                    item { HelpText(stringResource(R.string.carplay_skip_splash_help)) }
                    items(screens, key = { it.flattenToString() }) { component ->
                        val flat = component.flattenToString()
                        ListRow((if (flat == target) "● " else "") + component.className.substringAfterLast('.'), component.className) {
                            setTarget(flat)
                            container.installedApps.launchIntent(flat)?.let { safeStart(context, it) }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.carplay_why_title)) }
            item { HelpText(stringResource(R.string.carplay_why)) }
        }
    }
}

private fun kindHelp(kind: EngineKind): Int = when (kind) {
    EngineKind.NATIVE -> R.string.carplay_kind_native
    EngineKind.ZLINK -> R.string.carplay_kind_zlink
    EngineKind.ADAPTER -> R.string.carplay_kind_adapter
    EngineKind.MIRROR -> R.string.carplay_kind_mirror
}
