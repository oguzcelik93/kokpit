package io.github.yorulmazsinan.kokpit.drawer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(apps: InstalledApps, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenPlayer: () -> Unit = {}) {
    val context = LocalContext.current
    val entries by produceState(emptyList<AppEntry>()) { value = withContext(Dispatchers.IO) { apps.launchable() } }
    ScreenScaffold(stringResource(R.string.dock_apps), onBack) {
        ListRow(stringResource(R.string.player_title), stringResource(R.string.drawer_player_hint), onClick = onOpenPlayer)
        ListRow(stringResource(R.string.settings_title), stringResource(R.string.drawer_settings_hint), onClick = onOpenSettings)
        LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.padding(horizontal = 12.dp)) {
            items(entries, key = { it.component.flattenToString() }) { app ->
                val icon by produceState<ImageBitmap?>(null, app.component) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { context.packageManager.getActivityIcon(app.component).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                }
                Column(
                    Modifier
                        .heightIn(min = 120.dp)
                        .combinedClickable(
                            onClick = { safeStart(context, launcherIntent(app.component)) },
                            onLongClick = {
                                safeStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                            },
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon?.let { Image(it, null, Modifier.size(64.dp)) } ?: Spacer(Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
