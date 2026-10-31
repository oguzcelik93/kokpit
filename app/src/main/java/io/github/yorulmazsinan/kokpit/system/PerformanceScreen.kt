package io.github.yorulmazsinan.kokpit.system

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader

/**
 * Speed-ups that need no root: free RAM, faster animations (once WRITE_SECURE_SETTINGS is
 * granted over ADB), and a list of known useless apps that open their info page so the user
 * can switch them off — or back on — with Android's own button. Fully reversible.
 */
@Composable
fun PerformanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    // Re-read when coming back from an app's info page.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val bloat = remember(refresh) { Performance.knownBloat(pm).filter { it.enabled } }
    val disabled = remember(refresh) { Performance.disabledApps(pm) }
    val memory = remember(refresh) { Performance.memorySummary(context) }
    val canAnimate = remember(refresh) { Performance.canWriteSecureSettings(context) }
    var scale by remember(refresh) { mutableStateOf(Performance.currentAnimationScale(context)) }

    var memoryFreed by remember { mutableStateOf(false) }
    val status = remember(refresh, memoryFreed) { SpeedUp.readStatus(context, memoryFreed) }
    androidx.compose.runtime.LaunchedEffect(Unit) { SpeedUp.markWizardSeen(context) }
    ScreenScaffold(stringResource(R.string.performance_title), onBack) {
        LazyColumn {
            item {
                SectionHeader(
                    if (status.remaining == 0) stringResource(R.string.speedup_all_done)
                    else stringResource(R.string.speedup_header, SpeedUpStep.entries.size - status.remaining, SpeedUpStep.entries.size),
                )
            }
            item {
                StepRow(1, SpeedUpStep.MEMORY in status.done, stringResource(R.string.speedup_memory), stringResource(R.string.performance_memory_state, memory.first, memory.second)) {
                    val freed = Performance.freeMemory(context)
                    Toast.makeText(context, context.getString(R.string.performance_freed, freed), Toast.LENGTH_SHORT).show()
                    memoryFreed = true
                    refresh++
                }
            }
            item {
                StepRow(
                    2, SpeedUpStep.BLOAT in status.done, stringResource(R.string.speedup_bloat),
                    if (status.bloatLeft == 0) stringResource(R.string.performance_bloat_clean) else stringResource(R.string.speedup_bloat_left, status.bloatLeft),
                ) {
                    // One at a time: open the next app's page; coming back refreshes the count.
                    bloat.firstOrNull()?.let { safeStart(context, Performance.appInfoIntent(it.packageName)) }
                }
            }
            item {
                StepRow(
                    3, SpeedUpStep.ANIMATIONS in status.done, stringResource(R.string.speedup_animations),
                    if (canAnimate) stringResource(R.string.speedup_animations_auto) else stringResource(R.string.speedup_animations_manual),
                ) {
                    if (canAnimate) {
                        Performance.setAnimationScale(context, 0.5f)
                        scale = Performance.currentAnimationScale(context)
                        refresh++
                    } else if (!safeStartQuiet(context, SpeedUp.developerOptionsIntent())) {
                        Toast.makeText(context, R.string.speedup_dev_hidden, Toast.LENGTH_LONG).show()
                        safeStart(context, SpeedUp.aboutPhoneIntent())
                    }
                }
            }
            item {
                StepRow(4, SpeedUpStep.BATTERY in status.done, stringResource(R.string.speedup_battery), stringResource(R.string.speedup_battery_help)) {
                    safeStart(context, SpeedUp.batteryExemptIntent(context))
                }
            }

            elevatedSection { refresh++ }

            item { SectionHeader(stringResource(R.string.performance_memory)) }
            item {
                ListRow(stringResource(R.string.performance_free_memory), stringResource(R.string.performance_memory_state, memory.first, memory.second)) {
                    val freed = Performance.freeMemory(context)
                    Toast.makeText(context, context.getString(R.string.performance_freed, freed), Toast.LENGTH_SHORT).show()
                    refresh++
                }
            }

            item { SectionHeader(stringResource(R.string.performance_animations)) }
            if (canAnimate) {
                listOf(0f to R.string.performance_anim_off, 0.5f to R.string.performance_anim_fast, 1f to R.string.performance_anim_stock).forEach { (value, label) ->
                    item(key = "anim_$value") {
                        ListRow((if (scale == value) "● " else "") + stringResource(label)) {
                            if (value == 1f) Performance.restoreAnimations(context) else Performance.setAnimationScale(context, value)
                            scale = Performance.currentAnimationScale(context)
                        }
                    }
                }
            } else {
                item { HelpText(stringResource(R.string.performance_anim_adb, context.packageName)) }
            }

            item { SectionHeader(stringResource(R.string.performance_bloat)) }
            item { HelpText(stringResource(R.string.performance_bloat_help)) }
            if (bloat.isEmpty()) item { HelpText(stringResource(R.string.performance_bloat_clean)) }
            items(bloat, key = { "b_" + it.packageName }) { app ->
                ListRow(app.label, app.description) { safeStart(context, Performance.appInfoIntent(app.packageName)) }
            }

            item { SectionHeader(stringResource(R.string.performance_disabled_apps)) }
            item { HelpText(stringResource(R.string.performance_disabled_help)) }
            items(disabled, key = { "d_" + it.packageName }) { app ->
                ListRow(app.label, app.packageName) { safeStart(context, Performance.appInfoIntent(app.packageName)) }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, done: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    ListRow((if (done) "✓ " else "$number. ") + title, subtitle, onClick)
}

/** Like safeStart, but silent: the caller has its own fallback. */
private fun safeStartQuiet(context: android.content.Context, intent: android.content.Intent): Boolean =
    runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
