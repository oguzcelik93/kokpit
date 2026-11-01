package io.github.yorulmazsinan.kokpit.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opens the phone panel and shows its QR. With no hotspot around, Kokpit can host its own
 * Wi-Fi: step 1 is a Wi-Fi QR (the phone camera joins it), step 2 the panel QR.
 */
@Composable
fun PhonePanelScreen(panel: PhonePanel, hotspot: LocalHotspot, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val status by panel.status.collectAsStateWithLifecycle()
    val network by hotspot.network.collectAsStateWithLifecycle()
    var problem by remember { mutableStateOf<String?>(null) }
    var noNetwork by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (panel.status.value == null) noNetwork = panel.open() == null }

    fun ownWifi() {
        problem = null
        hotspot.start { net, reason ->
            if (net == null) {
                problem = reason
                return@start
            }
            // The unit's own address appears a moment after the network starts.
            scope.launch {
                repeat(10) {
                    if (panel.open() != null) { noNetwork = false; return@launch }
                    delay(500)
                }
                problem = "Ağ kuruldu ama adres alınamadı"
            }
        }
    }

    ScreenScaffold(stringResource(R.string.panel_title), onBack) {
        LazyColumn {
            val net = network
            if (net != null) {
                item { SectionHeader(stringResource(R.string.panel_step_wifi)) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrCode(wifiQr(net.ssid, net.password), Modifier.size(220.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.panel_wifi_scan), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Text(stringResource(R.string.panel_wifi_manual, net.ssid, net.password), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val s = status
            if (s != null) {
                if (net != null) item { SectionHeader(stringResource(R.string.panel_step_panel)) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrCode(s.url, Modifier.size(if (net != null) 220.dp else 260.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.panel_scan), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                            Text(stringResource(if (panel.pinSet) R.string.panel_scan_help_pin else R.string.panel_scan_help_newpin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { panel.close() }, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                                Text(stringResource(R.string.panel_close))
                            }
                        }
                    }
                }
                item { HelpText(stringResource(R.string.panel_safety)) }
            } else {
                if (noNetwork && net == null) item { HelpText(stringResource(R.string.panel_no_network)) }
                item {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { noNetwork = panel.open() == null }, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                            Text(stringResource(R.string.panel_open))
                        }
                        OutlinedButton(onClick = ::ownWifi, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                            Text(stringResource(R.string.panel_own_wifi))
                        }
                    }
                }
            }
            problem?.let { item { HelpText(it) } }
            if (s != null && net == null) {
                item { ListRow(stringResource(R.string.panel_own_wifi), stringResource(R.string.panel_own_wifi_help), onClick = ::ownWifi) }
            }
            if (panel.pinSet) {
                item { ListRow(stringResource(R.string.panel_pin_reset), stringResource(R.string.panel_pin_reset_help)) { confirmReset = true } }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.panel_pin_reset)) },
            text = { Text(stringResource(R.string.panel_pin_reset_confirm)) },
            confirmButton = { TextButton(onClick = { panel.resetPin(); confirmReset = false }) { Text(stringResource(R.string.recovery_reset_yes)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
