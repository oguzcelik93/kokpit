package io.github.yorulmazsinan.kokpit.connect

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Paired phones; tap one to make it the connected phone. */
@Composable
fun BtPhonesScreen(bt: BtAutoConnect, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    val phones = remember(tick) { bt.phones() }
    ScreenScaffold(stringResource(R.string.bt_phones_title), onBack) {
        LazyColumn {
            item { HelpText(stringResource(R.string.bt_phones_help)) }
            if (!bt.available || phones.isEmpty()) item { HelpText(stringResource(R.string.bt_phones_none)) }
            items(phones, key = { it.address }) { phone ->
                ListRow(
                    (if (phone.connected) "● " else "") + phone.name,
                    stringResource(if (phone.connected) R.string.bt_phone_connected else R.string.bt_phone_tap),
                ) {
                    if (phone.connected) return@ListRow
                    status = context.getString(R.string.bt_phone_connecting, phone.name)
                    scope.launch {
                        val ok = bt.switchTo(phone.address)
                        status = context.getString(if (ok) R.string.bt_phone_done else R.string.bt_phone_failed, phone.name)
                        tick++
                    }
                }
            }
            status?.let { item { HelpText(it) } }
        }
    }
}
