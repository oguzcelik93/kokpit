package io.github.yorulmazsinan.kokpit.system

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Speed-up screen → "Advanced access": one-tap speed-up over the unit's own ADB (opt-in). */
fun LazyListScope.elevatedSection(onChanged: () -> Unit) {
    item { SectionHeader(stringResource(R.string.elevated_title)) }
    item { ElevatedRows(onChanged) }
}

@Composable
private fun ElevatedRows(onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adb = remember { LocalAdb(context) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { available = withContext(Dispatchers.IO) { adb.isAvailable() } }

    fun run(work: () -> String) {
        if (busy) return
        busy = true
        lastResult = context.getString(R.string.elevated_wait)
        scope.launch {
            lastResult = withContext(Dispatchers.IO) {
                runCatching(work).getOrElse { context.getString(R.string.elevated_failed, it.message ?: it.javaClass.simpleName) }
            }
            busy = false
            onChanged()
        }
    }

    HelpText(stringResource(R.string.elevated_help))
    when (available) {
        null -> HelpText(stringResource(R.string.elevated_checking))
        false -> HelpText(stringResource(R.string.elevated_unavailable))
        true -> {
            ListRow(stringResource(R.string.elevated_speedup), stringResource(R.string.elevated_speedup_help)) {
                run {
                    val bloat = Performance.knownBloat(context.packageManager).filter { it.enabled }.map { it.packageName }
                    val r = adb.speedUp(bloat)
                    if (r.permissionGranted) Performance.setAnimationScale(context, 0.5f)
                    context.getString(R.string.elevated_done, r.disabled.size, r.failed.size)
                }
            }
            if (adb.disabledByKokpit.isNotEmpty()) {
                ListRow(stringResource(R.string.elevated_restore), stringResource(R.string.elevated_restore_help, adb.disabledByKokpit.size)) {
                    run {
                        Performance.restoreAnimations(context)
                        context.getString(R.string.elevated_restored, adb.restore().size)
                    }
                }
            }
        }
    }
    lastResult?.let { HelpText(it) }
    // Security: once done, ADB need not stay on; everything Kokpit changed persists without it.
    ListRow(stringResource(R.string.elevated_turn_off_debugging), stringResource(R.string.elevated_turn_off_debugging_help)) {
        safeStart(context, SpeedUp.developerOptionsIntent())
    }
    if (adb.hasKey) {
        ListRow(stringResource(R.string.elevated_forget), stringResource(R.string.elevated_forget_help)) {
            adb.forget()
            Toast.makeText(context, R.string.elevated_forgotten, Toast.LENGTH_SHORT).show()
            onChanged()
        }
    }
}
