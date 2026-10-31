package io.github.yorulmazsinan.kokpit.system

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow

/**
 * Everything needed to put the unit back the way it came, reachable even when the rest of
 * Kokpit misbehaves: from safe mode, the hidden clock long-press, or Settings → System.
 * Uses no repositories, only Android itself.
 */
@Composable
fun RecoveryScreen(onBack: (() -> Unit)?, onOpenPerformance: (() -> Unit)? = null) {
    val context = LocalContext.current
    var safe by remember { mutableStateOf(Recovery.isSafeMode(context)) }
    var confirmReset by remember { mutableStateOf(false) }
    val reason = remember(safe) { Recovery.safeModeReason(context) }
    val hasVendorHome = remember { vendorHomeShortcut(context.packageManager, context.packageName) != null }

    ScreenScaffold(stringResource(R.string.recovery_title), onBack) {
        LazyColumn {
            if (safe) {
                item {
                    HelpText(
                        stringResource(
                            when (reason) {
                                "crash" -> R.string.recovery_safe_crash
                                "usb" -> R.string.recovery_safe_usb
                                else -> R.string.recovery_safe_manual
                            },
                        ),
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.recovery_stock)) }
            if (hasVendorHome) {
                item { ListRow(stringResource(R.string.recovery_open_vendor), stringResource(R.string.recovery_open_vendor_help)) { Recovery.openVendorHome(context) } }
            }
            item {
                ListRow(stringResource(R.string.recovery_hand_back), stringResource(R.string.recovery_hand_back_help)) {
                    Recovery.handBack(context, "manual")
                    safe = true
                }
            }
            item {
                ListRow(stringResource(R.string.recovery_clear_home), stringResource(R.string.recovery_clear_home_help)) {
                    Recovery.clearHomeChoice(context)
                    safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            item {
                ToggleRow(stringResource(R.string.recovery_safe_mode), safe, stringResource(R.string.recovery_safe_mode_help)) { on ->
                    Recovery.setSafeMode(context, on, "manual")
                    safe = on
                    if (on) Recovery.clearHomeChoice(context) else Recovery.restart(context)
                }
            }

            item { SectionHeader(stringResource(R.string.recovery_kokpit)) }
            item { ListRow(stringResource(R.string.recovery_reset_settings), stringResource(R.string.recovery_reset_settings_help)) { confirmReset = true } }
            item {
                ListRow(stringResource(R.string.recovery_restore_animations), stringResource(R.string.recovery_restore_animations_help)) {
                    Performance.restoreAnimations(context)
                }
            }
            onOpenPerformance?.let { open ->
                item { ListRow(stringResource(R.string.performance_disabled_apps), stringResource(R.string.recovery_disabled_help), onClick = open) }
            }
            item {
                ListRow(stringResource(R.string.recovery_uninstall), stringResource(R.string.recovery_uninstall_help)) {
                    safeStart(context, Performance.appInfoIntent(context.packageName))
                }
            }

            item { SectionHeader(stringResource(R.string.recovery_ways)) }
            item { HelpText(stringResource(R.string.recovery_ways_help)) }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.recovery_reset_settings)) },
            text = { Text(stringResource(R.string.recovery_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    Recovery.wipeKokpitSettings(context)
                    Recovery.setSafeMode(context, false)
                    Recovery.restart(context)
                }) { Text(stringResource(R.string.recovery_reset_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
