package io.github.yorulmazsinan.kokpit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.yorulmazsinan.kokpit.assistant.AssistantNavigator
import io.github.yorulmazsinan.kokpit.assistant.AssistantPanel
import io.github.yorulmazsinan.kokpit.calls.CallBar
import io.github.yorulmazsinan.kokpit.dock.AppPickerScreen
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.drawer.AppDrawerScreen
import io.github.yorulmazsinan.kokpit.headunit.HeadUnitPhone
import io.github.yorulmazsinan.kokpit.headunit.VendorSource
import io.github.yorulmazsinan.kokpit.home.HomeRoute
import io.github.yorulmazsinan.kokpit.home.HomeUiState
import io.github.yorulmazsinan.kokpit.home.HomeViewModel
import io.github.yorulmazsinan.kokpit.home.cards.Dock
import io.github.yorulmazsinan.kokpit.intro.IntroSettingsScreen
import io.github.yorulmazsinan.kokpit.music.BtMusicScreen
import io.github.yorulmazsinan.kokpit.phone.PhoneScreen
import io.github.yorulmazsinan.kokpit.player.PlayerScreen
import io.github.yorulmazsinan.kokpit.radio.RadioPreset
import io.github.yorulmazsinan.kokpit.radio.stationNameFor
import io.github.yorulmazsinan.kokpit.radio.withSlot
import io.github.yorulmazsinan.kokpit.radio.RadioScreen
import io.github.yorulmazsinan.kokpit.settings.QuickSettingsScreen
import io.github.yorulmazsinan.kokpit.settings.SettingsScreen
import io.github.yorulmazsinan.kokpit.system.ActivityExplorerScreen
import io.github.yorulmazsinan.kokpit.system.DiagnosticsScreen
import io.github.yorulmazsinan.kokpit.system.PackageActivitiesScreen
import io.github.yorulmazsinan.kokpit.system.SystemToolsScreen
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.system.PerformanceScreen
import io.github.yorulmazsinan.kokpit.system.Recovery
import io.github.yorulmazsinan.kokpit.system.RecoveryScreen
import io.github.yorulmazsinan.kokpit.carplay.CarPlayScreen
import io.github.yorulmazsinan.kokpit.car.LogbookScreen
import io.github.yorulmazsinan.kokpit.update.UpdatePill
import io.github.yorulmazsinan.kokpit.update.InfoPill
import io.github.yorulmazsinan.kokpit.system.SpeedUp
import io.github.yorulmazsinan.kokpit.intro.IntroGate
import io.github.yorulmazsinan.kokpit.system.HomeGuard
import io.github.yorulmazsinan.kokpit.connect.PowerEventLog
import io.github.yorulmazsinan.kokpit.update.UpdateScreen
import io.github.yorulmazsinan.kokpit.update.UpdateState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.home.cards.IdleScreen
import io.github.yorulmazsinan.kokpit.home.cards.shouldIdle
import io.github.yorulmazsinan.kokpit.intro.currentTemperature
import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.yorulmazsinan.kokpit.car.ParkingScreen
import io.github.yorulmazsinan.kokpit.car.PlacesScreen
import io.github.yorulmazsinan.kokpit.car.navigateTo
import io.github.yorulmazsinan.kokpit.home.cards.NightPanel
import io.github.yorulmazsinan.kokpit.home.cards.BrightnessPanel
import io.github.yorulmazsinan.kokpit.ui.components.DockedLayout
import io.github.yorulmazsinan.kokpit.ui.theme.DesignScale
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KokpitApp).container
        hideSystemBars()
        if (Recovery.isSafeMode(this)) {
            startSafeMode(intent)
            return
        }
        // First start in this process: the welcome clip comes first, and the home screen is
        // only drawn once it is over, so a slow unit spends all its power on the video.
        if (savedInstanceState == null && !introCheckedThisProcess) {
            introCheckedThisProcess = true
            if (startIntroIfWanted(container)) {
                contentPending = true
                return
            }
        }
        showContent(container)
    }

    private var contentPending = false

    private fun startIntroIfWanted(container: AppContainer): Boolean {
        if (IntroGate.recentlyPlayed()) return false
        // Read from disk: right after a cold start the in-memory settings may not be loaded yet.
        val intro = kotlinx.coroutines.runBlocking { container.settings.current().intro }
        val videos = io.github.yorulmazsinan.kokpit.intro.allIntroVideos(container.introLibrary)
        val season = io.github.yorulmazsinan.kokpit.intro.seasonFor(
            io.github.yorulmazsinan.kokpit.time.TrueTime.date(),
            io.github.yorulmazsinan.kokpit.intro.currentTemperature(container.weather.state.value),
        )
        val video = io.github.yorulmazsinan.kokpit.intro.pickIntroVideo(intro, videos, null, kotlin.random.Random.Default, season) ?: return false
        if (!io.github.yorulmazsinan.kokpit.connect.introAllowedAt(container.drive.state.value.speedKmh)) return false
        IntroGate.markStarted()
        return safeStart(this, io.github.yorulmazsinan.kokpit.intro.IntroActivity.intent(this, video.id, intro.withSound))
    }

    private fun showContent(container: AppContainer) {
        setContent {
            val home: HomeViewModel = viewModel { HomeViewModel(container) }
            val state by home.state.collectAsStateWithLifecycle()
            KokpitTheme(dark = state.dark, accent = state.accent) {
                // One scale for every screen, so sizes match wherever you go.
                DesignScale { KokpitShell(container, state) }
            }
        }
    }

    private val markStable = Runnable { Recovery.markStable(this) }

    override fun onStart() {
        super.onStart()
        PowerEventLog.add(this, "${java.time.LocalTime.now().withNano(0)} Kokpit ekranda")
    }

    override fun onStop() {
        // Who took the screen, and when: the diagnostics report shows these next to power events.
        PowerEventLog.add(this, "${java.time.LocalTime.now().withNano(0)} Kokpit arka planda" + if (isFinishing) " (kapandı)" else "")
        if (!Recovery.isSafeMode(this)) {
            HomeGuard.onKokpitStopped(this, (application as KokpitApp).container.settings.settings.value.keepHome)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        if (contentPending) {
            contentPending = false
            showContent((application as KokpitApp).container)
        }
        // Half a minute on screen without crashing ends any crash-loop count.
        window.decorView.postDelayed(markStable, 30_000)
    }

    override fun onPause() {
        isResumed = false
        window.decorView.removeCallbacks(markStable)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Recovery.isSafeMode(this) && intent.hasCategory(Intent.CATEGORY_HOME)) Recovery.openVendorHome(this)
    }

    /**
     * Safe mode: Home goes straight on to the manufacturer launcher; opening Kokpit from its
     * icon (or when there is no other launcher) shows only the Recovery screen.
     */
    private fun startSafeMode(intent: Intent?) {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true && Recovery.openVendorHome(this)) {
            finish()
            return
        }
        setContent {
            KokpitTheme {
                DesignScale { RecoveryScreen(onBack = null) }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Kokpit owns the whole screen; a swipe from the edge brings the bars back for a moment. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        /** The welcome clip is offered once per process, at Kokpit's first start. */
        private var introCheckedThisProcess = false

        /** Kokpit is on screen right now. */
        @Volatile var isResumed = false
            private set

        /** Back to Kokpit after a vendor screen took over (its audio source keeps playing). */
        fun bringToFront(context: Context) {
            safeStart(context, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
}

object Routes {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val SETTINGS = "settings"
    const val INTRO = "intro"
    const val PHONE = "phone"
    const val MUSIC = "music"
    const val RADIO = "radio"
    const val PICKER = "picker/{slot}"
    const val SYSTEM_TOOLS = "system-tools"
    const val EXPLORER = "explorer"
    const val PACKAGE_ACTIVITIES = "explorer/{pkg}"
    const val DIAGNOSTICS = "diagnostics"
    const val QUICK = "quick"
    const val PLAYER = "player"
    const val CARPLAY = "carplay"
    const val PERFORMANCE = "performance"
    const val RECOVERY = "recovery"
    const val PARKING = "parking"
    const val FUEL = "fuel"
    const val PLACES = "places"
    const val UPDATE = "update"
    const val BT_PHONES = "bt-phones"
    const val WINDOW = "window"
    const val PHONE_PANEL = "phone-panel"
}

/** Dock slots with a Kokpit screen; those screens and home keep the dock on the bottom. */
private val KOKPIT_SCREENS = mapOf(DockSlot.PHONE to Routes.PHONE, DockSlot.BLUETOOTH to Routes.MUSIC, DockSlot.RADIO to Routes.RADIO)
private val DOCKED_ROUTES = setOf(Routes.HOME, Routes.PLAYER) + KOKPIT_SCREENS.values

@Composable
private fun KokpitShell(container: AppContainer, homeState: HomeUiState) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val active = KOKPIT_SCREENS.entries.firstOrNull { it.value == route }?.key

    fun launchSlot(slot: DockSlot): Boolean {
        val intent = homeState.dock[slot]?.let(container.installedApps::launchIntent) ?: return false
        return safeStart(context, intent)
    }

    fun openSlot(slot: DockSlot) {
        val screen = KOKPIT_SCREENS[slot]
        when {
            slot == DockSlot.APPS -> nav.navigate(Routes.DRAWER)
            // Tapping the lit button again goes home.
            slot == active -> nav.popBackStack(Routes.HOME, inclusive = false)
            screen != null && container.settings.settings.value.useKokpitScreens -> nav.navigate(screen) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
            !launchSlot(slot) -> nav.navigate("picker/${slot.name}")
        }
    }

    fun longPressSlot(slot: DockSlot) {
        if (slot == DockSlot.APPS) nav.navigate(Routes.SETTINGS) else nav.navigate("picker/${slot.name}")
    }

    val dock: (@Composable () -> Unit)? = if (route in DOCKED_ROUTES) {
        { Dock(::openSlot, ::longPressSlot, active = active) }
    } else {
        null
    }
    val call by container.callMonitor.call.collectAsStateWithLifecycle()
    // The vendor's phone service pops its own call window over every app; ours would only double it.
    val banner: (@Composable () -> Unit)? = call?.takeIf { container.headUnit.phone == null }?.let { active ->
        {
            CallBar(
                active,
                onAnswer = { container.callMonitor.answer() },
                onHangUp = { container.callMonitor.hangUp() },
                onOpen = { if (route != Routes.PHONE) nav.navigate(Routes.PHONE) { launchSingleTop = true } },
            )
        }
    }
    // The navigator outlives recompositions; always reach the latest dock state.
    val openDockSlot by rememberUpdatedState<(DockSlot) -> Unit>(::openSlot)
    DisposableEffect(nav) {
        container.assistantNavigator = object : AssistantNavigator {
            override fun openRoute(route: String) {
                if (route == Routes.HOME) nav.popBackStack(Routes.HOME, inclusive = false) else nav.navigate(route) { launchSingleTop = true }
            }

            override fun openSlot(slot: DockSlot) = openDockSlot(slot)
        }
        onDispose { container.assistantNavigator = null }
    }
    val assistant by container.assistant.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { container.assistant.open() }
    val nightPanel by container.nightPanel.collectAsStateWithLifecycle()
    // Stand-by clock: any touch anywhere counts as activity (observed, never consumed).
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var idle by remember { mutableStateOf(false) }
    val latestTrip by rememberUpdatedState(homeState.trip)
    val nightPanelOn by rememberUpdatedState(nightPanel)
    LaunchedEffect(route, settings.idleScreen, settings.idleMinutes) {
        while (true) {
            val speed = latestTrip.speedKmh.takeIf { latestTrip.hasFix }
            idle = when {
                idle -> speed == null || speed < 10
                else -> shouldIdle(settings.idleScreen, route == Routes.HOME, SystemClock.elapsedRealtime() - lastTouch, settings.idleMinutes, speed)
            } && !nightPanelOn
            delay(3_000)
        }
    }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastTouch = SystemClock.elapsedRealtime()
                }
            }
        },
    ) {
        DockedLayout(dock, banner) { KokpitNavHost(nav, container, homeState, ::openSlot, ::longPressSlot) }
        if (nightPanel) NightPanel { container.nightPanel.value = false }
        val brightness by container.brightnessPanel.collectAsStateWithLifecycle()
        if (brightness) BrightnessPanel(onDismiss = { container.brightnessPanel.value = false })
        val update by container.updater.state.collectAsStateWithLifecycle()
        // While the phone panel is open, say so (and offer to close it) on the home screen.
        val panelStatus by container.phonePanel.status.collectAsStateWithLifecycle()
        if (route == Routes.HOME && panelStatus != null) {
            InfoPill(androidx.compose.ui.res.stringResource(R.string.panel_pill), Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
                nav.navigate(Routes.PHONE_PANEL) { launchSingleTop = true }
            }
        }
        // Until the speed-up checklist has been opened once, a reminder sits on the home screen.
        val speedUpSeen = remember(route) { SpeedUp.wizardSeen(context) }
        if (route == Routes.HOME && (update is UpdateState.Ready || update is UpdateState.Available)) {
            UpdatePill(update, Modifier.align(Alignment.TopCenter)) { nav.navigate(Routes.UPDATE) { launchSingleTop = true } }
        } else if (route == Routes.HOME && !speedUpSeen) {
            InfoPill(androidx.compose.ui.res.stringResource(R.string.speedup_pill), Modifier.align(Alignment.TopCenter)) {
                nav.navigate(Routes.PERFORMANCE) { launchSingleTop = true }
            }
        }
        if (idle) {
            IdleScreen(currentTemperature(homeState.weather), onExit = {
                idle = false
                lastTouch = SystemClock.elapsedRealtime()
            })
        }
        AssistantPanel(
            state = assistant,
            providerLabel = settings.assistant.provider.label + " · " + settings.assistant.model,
            onClose = container.assistant::close,
            onTalk = container.assistant::open,
            onOpenSettings = {
                container.assistant.close()
                nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
            },
            onGrantMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
        )
    }
}

/**
 * Switches the audio source to [source] without leaving Kokpit. Only when the unit refuses
 * does it fall back to opening the vendor screen and bringing Kokpit straight back.
 */
@Composable
private fun SwitchVendorSource(
    container: AppContainer,
    source: VendorSource,
    beforeSwitch: () -> Unit = {},
    afterSwitch: suspend () -> Unit = {},
    open: () -> Boolean?,
) {
    val context = LocalContext.current
    val lifecycle = (context as ComponentActivity).lifecycle
    LaunchedEffect(source) {
        val modes = container.vendorModes
        if (withContext(Dispatchers.IO) { modes.isActive(source) }) return@LaunchedEffect
        withContext(Dispatchers.IO) { beforeSwitch() }
        if (!withContext(Dispatchers.IO) { modes.enter(source) }) {
            if (withContext(Dispatchers.IO) { open() } != true) return@LaunchedEffect
            val left = withTimeoutOrNull(3_000) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) } }
            if (left != null) MainActivity.bringToFront(context)
            delay(1_500)
        }
        withContext(Dispatchers.IO) { afterSwitch() }
    }
}

@Composable
private fun KokpitNavHost(
    nav: NavHostController,
    container: AppContainer,
    homeState: HomeUiState,
    onDock: (DockSlot) -> Unit,
    onDockLongPress: (DockSlot) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(
                container = container,
                state = homeState,
                onOpenSettings = { nav.navigate(Routes.QUICK) },
                onPickApp = { nav.navigate("picker/${it.name}") },
                onDock = onDock,
                onDockLongPress = onDockLongPress,
                onRecovery = { nav.navigate(Routes.RECOVERY) { launchSingleTop = true } },
                onOpenParking = { nav.navigate(Routes.PARKING) { launchSingleTop = true } },
                onOpenFuel = { nav.navigate(Routes.FUEL) { launchSingleTop = true } },
                onOpenPlaces = { nav.navigate(Routes.PLACES) { launchSingleTop = true } },
                onOpenWindow = { nav.navigate(Routes.WINDOW) { launchSingleTop = true } },
            )
        }
        composable(Routes.PHONE_PANEL) { io.github.yorulmazsinan.kokpit.panel.PhonePanelScreen(container.phonePanel, onBack = { nav.popBackStack() }) }
        composable(Routes.WINDOW) {
            io.github.yorulmazsinan.kokpit.home.WindowScreen(container, onBack = { nav.popBackStack() }, onPickApp = { nav.navigate("picker/${it.name}") })
        }
        composable(Routes.BT_PHONES) { io.github.yorulmazsinan.kokpit.connect.BtPhonesScreen(container.btConnect, onBack = { nav.popBackStack() }) }
        composable(Routes.UPDATE) { UpdateScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.PARKING) { ParkingScreen(container.car, container.drive::lastPosition, onBack = { nav.popBackStack() }) }
        composable(Routes.FUEL) { LogbookScreen(container.car, container.trips, onBack = { nav.popBackStack() }) }
        composable(Routes.PLACES) {
            PlacesScreen(
                container.car,
                container.drive::lastPosition,
                onNavigate = { kind -> navigateTo(context, container, kind, homeState.dock[DockSlot.MAPS]) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.CARPLAY) { CarPlayScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.PERFORMANCE) { PerformanceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RECOVERY) {
            RecoveryScreen(onBack = { nav.popBackStack() }, onOpenPerformance = { nav.navigate(Routes.PERFORMANCE) })
        }
        composable(Routes.PICKER) { entry ->
            val slot = DockSlot.valueOf(entry.arguments?.getString("slot").orEmpty())
            AppPickerScreen(slot, onBack = { nav.popBackStack() }) { pkg ->
                scope.launch { container.dock.setOverride(slot, pkg) }
                nav.popBackStack()
            }
        }
        composable(Routes.DRAWER) {
            AppDrawerScreen(
                container.installedApps,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenPlayer = { nav.navigate(Routes.PLAYER) },
            )
        }
        composable(Routes.QUICK) {
            QuickSettingsScreen(container, onBack = { nav.popBackStack() }, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.PLAYER) { PlayerScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onPickApp = { nav.navigate("picker/${it.name}") },
                onOpenIntro = { nav.navigate(Routes.INTRO) },
                onOpenSystemTools = { nav.navigate(Routes.SYSTEM_TOOLS) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenPreview = { nav.navigate(it) },
                onOpenCarPlay = { nav.navigate(Routes.CARPLAY) },
                onOpenPerformance = { nav.navigate(Routes.PERFORMANCE) },
                onOpenRecovery = { nav.navigate(Routes.RECOVERY) },
                onOpenPlaces = { nav.navigate(Routes.PLACES) },
                onOpenFuel = { nav.navigate(Routes.FUEL) },
                onOpenUpdate = { nav.navigate(Routes.UPDATE) },
                onOpenBtPhones = { nav.navigate(Routes.BT_PHONES) },
                onOpenWindow = { nav.navigate(Routes.WINDOW) },
                onOpenPhonePanel = { nav.navigate(Routes.PHONE_PANEL) },
            )
        }
        composable(Routes.INTRO) { IntroSettingsScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.SYSTEM_TOOLS) {
            SystemToolsScreen(
                onBack = { nav.popBackStack() },
                onOpenExplorer = { nav.navigate(Routes.EXPLORER) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
            )
        }
        composable(Routes.EXPLORER) {
            ActivityExplorerScreen(onBack = { nav.popBackStack() }, onPickPackage = { nav.navigate("explorer/$it") })
        }
        composable(Routes.PACKAGE_ACTIVITIES) { entry ->
            PackageActivitiesScreen(entry.arguments?.getString("pkg").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.PHONE) {
            val device by container.headUnit.btDevice.collectAsStateWithLifecycle()
            val calls by container.calls.calls.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                container.headUnit.refresh()
                container.calls.refresh()
            }
            PhoneScreen(
                calls = calls,
                deviceName = device,
                onBack = { nav.popBackStack() },
                vendorDial = { number -> container.headUnit.phone?.dial(number) == true },
            )
        }
        composable(Routes.MUSIC) {
            val vendor = container.headUnit.phone
            SwitchVendorSource(container, VendorSource.BT_MUSIC, container.btMusic::rememberBeforeSwitch, container.btMusic::resumeAfterSwitch) { vendor?.openMusic() }
            // The Bluetooth session's own state goes stale; re-read the head unit's while Music is open.
            LaunchedEffect(Unit) {
                while (true) {
                    container.nowPlaying.refresh()
                    delay(1000)
                }
            }
            fun vendorOr(fallback: () -> Unit, action: HeadUnitPhone.() -> Boolean) {
                scope.launch { if (withContext(Dispatchers.IO) { vendor?.action() } != true) fallback() }
            }
            BtMusicScreen(
                state = homeState.media,
                onBack = { nav.popBackStack() },
                onPrevious = { vendorOr(container.nowPlaying::previous) { previous() } },
                onPlayPause = container::playPause,
                onNext = { vendorOr(container.nowPlaying::next) { next() } },
                onGrantAccess = { safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
        }
        composable(Routes.RADIO) {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val live by container.headUnit.frequency.collectAsStateWithLifecycle()
            val rds by container.headUnit.station.collectAsStateWithLifecycle()
            val radio = container.headUnit.radio
            SwitchVendorSource(container, VendorSource.RADIO) { radio?.open() }
            LaunchedEffect(Unit) {
                while (true) {
                    container.headUnit.refresh()
                    delay(1000)
                }
            }
            val frequency = live ?: settings.radioFrequency
            fun tune(f: Int) {
                scope.launch {
                    withContext(Dispatchers.IO) { radio?.tune(f) }
                    container.settings.update { it.copy(radioFrequency = f) }
                    container.headUnit.refresh()
                }
            }
            RadioScreen(
                frequency = frequency,
                presets = settings.radioPresets,
                stationName = stationNameFor(frequency, rds, settings.radioPresets),
                onTune = ::tune,
                onSavePreset = { slot ->
                    val preset = RadioPreset(frequency, stationNameFor(frequency, rds, settings.radioPresets))
                    scope.launch { container.settings.update { it.copy(radioPresets = it.radioPresets.withSlot(slot, preset)) } }
                },
                onOpenVendorRadio = {
                    homeState.dock[DockSlot.RADIO]?.let(container.installedApps::launchIntent)?.let { safeStart(context, it) }
                },
                onBack = { nav.popBackStack() },
                connected = radio != null,
                onSeek = { direction ->
                    scope.launch {
                        withContext(Dispatchers.IO) { if (direction > 0) radio?.seekUp() else radio?.seekDown() }
                    }
                },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
    }
}
