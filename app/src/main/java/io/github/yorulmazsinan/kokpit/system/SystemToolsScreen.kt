package io.github.yorulmazsinan.kokpit.system

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold

@Composable
fun SystemToolsScreen(onBack: (() -> Unit)?, onOpenExplorer: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val shortcuts = remember {
        val pm = context.packageManager
        systemShortcuts().resolvable(pm) + listOfNotNull(vendorHomeShortcut(pm, context.packageName))
    }
    ScreenScaffold(stringResource(R.string.system_tools_title), onBack) {
        LazyColumn {
            items(shortcuts, key = { it.id }) { shortcut ->
                ListRow(stringResource(shortcut.label)) { safeStart(context, shortcut.intent) }
            }
            item { ListRow(stringResource(R.string.diagnostics_title), onClick = onOpenDiagnostics) }
            item { ListRow(stringResource(R.string.activity_explorer_title), onClick = onOpenExplorer) }
        }
    }
}

@Composable
fun ActivityExplorerScreen(onBack: () -> Unit, onPickPackage: (String) -> Unit) {
    val context = LocalContext.current
    val packages = remember { systemPackages(context.packageManager) }
    ScreenScaffold(stringResource(R.string.activity_explorer_title), onBack) {
        LazyColumn {
            items(packages, key = { it }) { pkg -> ListRow(pkg) { onPickPackage(pkg) } }
        }
    }
}

@Composable
fun PackageActivitiesScreen(packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activities = remember(packageName) { exportedActivities(context.packageManager, packageName) }
    ScreenScaffold(packageName, onBack) {
        if (activities.isEmpty()) {
            Text(
                stringResource(R.string.activity_explorer_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn {
            items(activities, key = { it.className }) { component: ComponentName ->
                ListRow(component.className.substringAfterLast('.'), component.className) {
                    safeStart(context, Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}
