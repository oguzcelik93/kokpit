package io.github.yorulmazsinan.kokpit.home

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.home.cards.WindowCardState
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.car.PlaceKind
import io.github.yorulmazsinan.kokpit.car.navigateTo
import io.github.yorulmazsinan.kokpit.car.saveHere
import io.github.yorulmazsinan.kokpit.home.cards.CarShortcuts
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RUNTIME_PERMISSIONS = listOfNotNull(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.READ_CALL_LOG,
    // Reading the USB stick's root is how the KOKPIT-KURTARMA recovery file is found.
    Manifest.permission.READ_EXTERNAL_STORAGE.takeIf { Build.VERSION.SDK_INT <= 32 },
    Manifest.permission.WRITE_EXTERNAL_STORAGE.takeIf { Build.VERSION.SDK_INT <= 28 },
).toTypedArray()

@Composable
fun HomeRoute(
    container: AppContainer,
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onPickApp: (DockSlot) -> Unit,
    onDock: (DockSlot) -> Unit,
    onDockLongPress: (DockSlot) -> Unit,
    onRecovery: () -> Unit = {},
    onOpenParking: () -> Unit = {},
    onOpenFuel: () -> Unit = {},
    onOpenTrack: (() -> Unit)? = null,
    onOpenPlaces: () -> Unit = {},
    onOpenWindow: () -> Unit = {},
) {
    val context = LocalContext.current
    val now by produceState(io.github.yorulmazsinan.kokpit.time.TrueTime.dateTime()) {
        while (true) {
            value = io.github.yorulmazsinan.kokpit.time.TrueTime.dateTime()
            delay(60_000L - (io.github.yorulmazsinan.kokpit.time.TrueTime.nowMs() % 60_000L))
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.drive.refreshPermission()
        container.timeSync.refreshPermission()
        container.calls.refresh()
        container.scope.launch {
            container.calendar.refresh()
            container.weather.refresh()
        }
    }
    LaunchedEffect(Unit) {
        if (container.permissionsRequested) return@LaunchedEffect
        container.permissionsRequested = true
        val missing = RUNTIME_PERMISSIONS.filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    LifecycleResumeEffect(Unit) {
        container.nowPlaying.refreshAccess()
        container.scope.launch { container.dock.refreshInstalled() }
        onPauseOrDispose { }
    }

    val wallpaper by container.wallpaper.image.collectAsStateWithLifecycle()
    val selectedPhoto by container.wallpaper.selected.collectAsStateWithLifecycle()
    val framings by container.wallpaper.framing.collectAsStateWithLifecycle()
    val browserTarget = state.dock[DockSlot.BROWSER]
    val browserIcon = remember(browserTarget) {
        browserTarget?.let { t ->
            runCatching {
                context.packageManager.getApplicationIcon(io.github.yorulmazsinan.kokpit.dock.parseDockTarget(t).packageName)
                    .toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }

    fun launchSlot(slot: DockSlot): Boolean {
        val intent = state.dock[slot]?.let(container.installedApps::launchIntent) ?: return false
        return safeStart(context, intent)
    }

    HomeScreen(
        state = state,
        now = now,
        actions = HomeActions(
            onDock = onDock,
            onDockLongPress = onDockLongPress,
            onOpenSettings = onOpenSettings,
            onOpenCalendar = {
                safeStart(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            },
            onOpenMaps = { if (!launchSlot(DockSlot.MAPS)) onPickApp(DockSlot.MAPS) },
            onRefreshWeather = { container.scope.launch { container.weather.refresh() } },
            // An app picked for the assistant slot wins; otherwise Kokpit's own assistant.
            onAssistant = { if (!launchSlot(DockSlot.ASSISTANT)) container.assistant.open() },
            onPlayPause = container::playPause,
            onNext = container.nowPlaying::next,
            onPrevious = container.nowPlaying::previous,
            onRequestPermissions = { permissionLauncher.launch(RUNTIME_PERMISSIONS) },
            onGrantMediaAccess = { safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            onNightPanel = { container.nightPanel.value = true },
            onBrightness = { container.brightnessPanel.value = true },
            onWindowOpen = { if (!launchSlot(DockSlot.BROWSER)) onOpenWindow() },
            onWindowSettings = onOpenWindow,
            onWindowSize = { w, h -> container.wallpaper.cardSizes.value += ((wallpaper?.portrait == true) to (w to h)) },
            car = CarShortcuts(
                onHome = { if (!navigateTo(context, container, PlaceKind.HOME, state.dock[DockSlot.MAPS])) onOpenPlaces() },
                onWork = { if (!navigateTo(context, container, PlaceKind.WORK, state.dock[DockSlot.MAPS])) onOpenPlaces() },
                onHomeLong = { saveHere(context, container, PlaceKind.HOME) },
                onWorkLong = { saveHere(context, container, PlaceKind.WORK) },
                onParking = onOpenParking,
                onFuel = onOpenFuel,
                onTrack = onOpenTrack,
            ),
            onRecovery = onRecovery,
        ),
        // The dock lives in the app shell so it stays put across Phone, Music and Radio.
        showDock = false,
        window = WindowCardState(wallpaper?.photo, browserTarget?.let(container.installedApps::label), browserIcon, wallpaper?.backdrop, wallpaper?.portrait == true,
            selectedPhoto?.let { framings[it] } ?: io.github.yorulmazsinan.kokpit.car.Framing()),
    )
}
