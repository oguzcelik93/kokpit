package io.github.yorulmazsinan.kokpit

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import androidx.datastore.preferences.preferencesDataStore
import io.github.yorulmazsinan.kokpit.calendar.CalendarRepository
import io.github.yorulmazsinan.kokpit.assistant.AssistantController
import io.github.yorulmazsinan.kokpit.assistant.AssistantNavigator
import io.github.yorulmazsinan.kokpit.assistant.DeviceActions
import io.github.yorulmazsinan.kokpit.calls.CallLogRepository
import io.github.yorulmazsinan.kokpit.calls.CallMonitor
import io.github.yorulmazsinan.kokpit.player.UsbPlayer
import io.github.yorulmazsinan.kokpit.connect.KokpitService
import io.github.yorulmazsinan.kokpit.car.AutoBrightness
import io.github.yorulmazsinan.kokpit.car.CarRepository
import io.github.yorulmazsinan.kokpit.car.ResumePlayback
import io.github.yorulmazsinan.kokpit.car.TripLog
import io.github.yorulmazsinan.kokpit.update.Updater
import io.github.yorulmazsinan.kokpit.time.TimeSync
import io.github.yorulmazsinan.kokpit.connect.BtAutoConnect
import io.github.yorulmazsinan.kokpit.storage.removableRoots
import kotlinx.coroutines.delay
import io.github.yorulmazsinan.kokpit.connect.UsbAttachActivity
import io.github.yorulmazsinan.kokpit.connect.WifiAutoConnector
import io.github.yorulmazsinan.kokpit.dock.DockRepository
import io.github.yorulmazsinan.kokpit.drawer.InstalledApps
import io.github.yorulmazsinan.kokpit.drive.DriveRepository
import io.github.yorulmazsinan.kokpit.headunit.BLUETOOTH_SESSION_PACKAGE
import io.github.yorulmazsinan.kokpit.headunit.BtMusic
import io.github.yorulmazsinan.kokpit.headunit.HeadUnit
import io.github.yorulmazsinan.kokpit.headunit.usesHeadUnitMusic
import io.github.yorulmazsinan.kokpit.headunit.VendorModes
import io.github.yorulmazsinan.kokpit.headunit.VendorSource
import io.github.yorulmazsinan.kokpit.drive.LocationSource
import io.github.yorulmazsinan.kokpit.drive.TripTracker
import io.github.yorulmazsinan.kokpit.home.ThemeMode
import io.github.yorulmazsinan.kokpit.intro.IntroVideoLibrary
import io.github.yorulmazsinan.kokpit.media.NowPlayingRepository
import io.github.yorulmazsinan.kokpit.settings.KeystoreSecretBox
import io.github.yorulmazsinan.kokpit.settings.SettingsRepository
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import io.github.yorulmazsinan.kokpit.weather.OpenMeteoClient
import io.github.yorulmazsinan.kokpit.weather.WeatherRepository
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private val Context.weatherStore by preferencesDataStore("weather")
private val Context.dockStore by preferencesDataStore("dock")
private val Context.settingsStore by preferencesDataStore("settings")

/** Work that waits until the welcome clip and the first frame are on screen. */
private const val DEFERRED_START_MS = 6_000L

/** Hand-wired holder for the app's long-lived repositories. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient()

    val locationSource = LocationSource(appContext)
    val drive = DriveRepository(scope, locationSource, TripTracker())
    val weather = WeatherRepository(OpenMeteoClient(http), appContext.weatherStore, { locationSource.lastKnown() ?: drive.lastPosition() })
    val calendar = CalendarRepository(appContext, scope)
    val nowPlaying = NowPlayingRepository(appContext)
    val calls = CallLogRepository(appContext, scope)
    val headUnit = HeadUnit(appContext, scope)
    val vendorModes = VendorModes(headUnit)
    val btMusic = BtMusic({ headUnit.phone }) { vendorModes.enter(VendorSource.BT_MUSIC) }
    val callMonitor = CallMonitor(appContext, scope, headUnit, calls)
    val usbPlayer by lazy { UsbPlayer(appContext) }

    /** Set by the app shell so the assistant can open Kokpit screens. */
    var assistantNavigator: AssistantNavigator? = null
    val assistant by lazy {
        AssistantController(appContext, scope, { settings.settings.value.assistant }, DeviceActions(appContext, this) { assistantNavigator })
    }
    val installedApps = InstalledApps(appContext.packageManager, appContext.packageName)
    val dock = DockRepository(appContext.dockStore, installedApps, scope)
    val settings = SettingsRepository(appContext.settingsStore, KeystoreSecretBox(), scope)
    val themeMode: Flow<ThemeMode> = settings.settings.map { it.themeMode }
    val accent: Flow<ThemeAccent> = settings.settings.map { it.accent }
    val introLibrary = IntroVideoLibrary(File(appContext.filesDir, "intro"))
    val wifi = WifiAutoConnector(appContext, settings)

    /** Saab Night Panel: only the speedometer stays lit. */
    val nightPanel = MutableStateFlow(false)

    /** The one-tap brightness panel, shown over any screen. */
    val brightnessPanel = MutableStateFlow(false)

    val car = CarRepository(appContext)
    val trips = TripLog(appContext)
    val updater = Updater(appContext, http, scope)
    val timeSync = TimeSync(appContext, http, scope)
    val btConnect = BtAutoConnect(appContext, scope)
    val resume = ResumePlayback(appContext, nowPlaying, vendorModes, btMusic)

    /** False between ignition off and on; nothing is sampled while the unit sleeps. */
    @Volatile var awake = true
    val autoBrightness by lazy { AutoBrightness(appContext, settings.settings, weather.state, nightPanel) }

    /** Runtime permissions are asked for once per process; afterwards the cards offer tap-to-grant. */
    var permissionsRequested = false

    /** Play/pause for home and Music: Bluetooth music by the head unit's own state, anything else by its session. */
    fun playPause() {
        scope.launch {
            val viaHeadUnit = usesHeadUnitMusic(nowPlaying.currentPackage()) &&
                withContext(Dispatchers.IO) { btMusic.toggle() }
            if (viaHeadUnit) nowPlaying.refresh() else nowPlaying.playPause()
        }
    }

    fun start() {
        nowPlaying.playingOverride = { pkg -> if (pkg == BLUETOOTH_SESSION_PACKAGE) btMusic.isPlaying() else null }
        // The process may have been (re)started while the unit was awake: run the wake-up connect now.
        wifi.reconnect(scope)
        scope.launch { dock.refreshInstalled() }
        scope.launch {
            drive.state.first { it.hasFix }
            if (weather.state.value !is WeatherState.Fresh) weather.refresh()
        }
        KokpitService.createChannel(appContext)
        KokpitService.start(appContext)
        scope.launch {
            settings.settings.map { it.autoCarPlayOnUsb }.distinctUntilChanged().collect(::setUsbReceiverEnabled)
        }
        drive.start()
        timeSync.start()
        autoBrightness.start(scope)
        // The process may start mid-drive (update, crash): open a trip from here.
        trips.begin(drive.distanceMeters())
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(60_000)
                if (!awake) continue
                trips.checkpoint(drive.distanceMeters())
                runCatching { resume.sample() }
            }
        }
        // Everything the first seconds don't need waits, so the welcome clip and the first
        // frame get the slow CPU to themselves.
        scope.launch {
            delay(DEFERRED_START_MS)
            weather.start(scope)
            calendar.start()
            calls.refresh()
            headUnit.start()
            callMonitor.start()
            nowPlaying.refreshAccess()
            btConnect.start()
            updater.startAuto { settings.settings.value.autoUpdate }
            // A stick left in since boot: look for a newer APK on it too.
            launch(Dispatchers.IO) { runCatching { updater.checkUsb(removableRoots(appContext)) } }
        }
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                timeSync.syncFromInternet()
                scope.launch { weather.refresh() }
            }
        })
    }

    /** The USB activity exists only while wanted, so it never adds a chooser next to the CarPlay app. */
    private fun setUsbReceiverEnabled(enabled: Boolean) {
        appContext.packageManager.setComponentEnabledSetting(
            ComponentName(appContext, UsbAttachActivity::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
