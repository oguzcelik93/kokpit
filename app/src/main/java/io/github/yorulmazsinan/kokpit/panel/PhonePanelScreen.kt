package io.github.yorulmazsinan.kokpit.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.car.QrCode
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens

/** Opens the phone panel and shows its QR code; the panel keeps running when you leave. */
@Composable
fun PhonePanelScreen(panel: PhonePanel, onBack: () -> Unit) {
    val status by panel.status.collectAsStateWithLifecycle()
    var noNetwork by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (panel.status.value == null) noNetwork = panel.open() == null }
    ScreenScaffold(stringResource(R.string.panel_title), onBack) {
        LazyColumn {
            val s = status
            if (s != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QrCode(s.url, Modifier.size(260.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.panel_scan), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                            Text(stringResource(R.string.panel_scan_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { panel.close() }, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                                Text(stringResource(R.string.panel_close))
                            }
                        }
                    }
                }
                item { HelpText(stringResource(R.string.panel_safety)) }
            } else {
                if (noNetwork) item { HelpText(stringResource(R.string.window_no_network)) }
                item {
                    Button(
                        onClick = { noNetwork = panel.open() == null },
                        shape = KokpitButtonShape,
                        modifier = Modifier.padding(20.dp).heightIn(min = Dimens.MinTouch),
                    ) { Text(stringResource(R.string.panel_open)) }
                }
            }
        }
    }
}
