package io.github.yorulmazsinan.kokpit.dock

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.drawer.InstalledApps
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold

/** Lets the user choose which app a dock slot opens; [onPick] gets null for "use default". */
@Composable
fun AppPickerScreen(slot: DockSlot, onBack: () -> Unit, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val apps = remember { InstalledApps(context.packageManager, context.packageName).launchable() }
    ScreenScaffold("${stringResource(R.string.app_picker_title)}: ${stringResource(slot.label)}", onBack) {
        LazyColumn {
            item { ListRow(stringResource(R.string.app_picker_default)) { onPick(null) } }
            items(apps, key = { it.component.flattenToString() }) { app ->
                ListRow(app.label, app.component.flattenToShortString()) { onPick(app.component.flattenToString()) }
            }
        }
    }
}
