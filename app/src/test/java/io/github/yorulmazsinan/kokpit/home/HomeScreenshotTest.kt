package io.github.yorulmazsinan.kokpit.home

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.yorulmazsinan.kokpit.calendar.CalendarState
import io.github.yorulmazsinan.kokpit.calendar.UpcomingEvent
import io.github.yorulmazsinan.kokpit.calls.CallEntry
import io.github.yorulmazsinan.kokpit.calls.CallType
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.drive.CompassPoint
import io.github.yorulmazsinan.kokpit.drive.TripSnapshot
import io.github.yorulmazsinan.kokpit.media.NowPlaying
import io.github.yorulmazsinan.kokpit.media.NowPlayingState
import io.github.yorulmazsinan.kokpit.ui.theme.DesignScale
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitTheme
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import io.github.yorulmazsinan.kokpit.home.cards.NightPanel
import io.github.yorulmazsinan.kokpit.home.cards.BrightnessPanel
import io.github.yorulmazsinan.kokpit.weather.Weather
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the home screen at common head-unit sizes into app/build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 10, 27, 15, 2)

    private fun state(dark: Boolean) = HomeUiState(
        weather = WeatherState.Fresh(
            Weather(18.4, 2, true, 21.0, 12.1, LocalTime.of(6, 58), LocalTime.of(17, 21), 0L),
        ),
        calendar = CalendarState.Next(
            UpcomingEvent("Müşteri toplantısı", now.withHour(16).withMinute(30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
        ),
        media = NowPlayingState.Playing(NowPlaying("Gesi Bağları", "Barış Manço", "Spotify", null, isPlaying = true)),
        trip = TripSnapshot(speedKmh = 62, compass = CompassPoint.NW, averageKmh = 48, hasFix = true, bearingDeg = 315f),
        locationPermissionMissing = false,
        dock = DockSlot.entries.associateWith { null },
        dark = dark,
        recentCalls = listOf(
            CallEntry("Ece", "0555", CallType.MISSED, 3L),
            CallEntry("Ofis", "0212", CallType.OUTGOING, 2L),
            CallEntry(null, "0532 111 22 33", CallType.INCOMING, 1L),
        ),
    )

    /** A stand-in photo: a dusk road gradient. */
    private fun photo(): androidx.compose.ui.graphics.ImageBitmap {
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, 360f, intArrayOf(0xFF1B2A4A.toInt(), 0xFFE07B39.toInt(), 0xFF202020.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 640f, 360f, paint)
        return bmp.asImageBitmap()
    }

    private fun capture(name: String, dark: Boolean = true, accent: ThemeAccent = ThemeAccent.GAUGE_GREEN) {
        val window = if (accent == ThemeAccent.SAAB_93) io.github.yorulmazsinan.kokpit.home.cards.WindowCardState(photo(), "Brave", null) else null
        compose.setContent { KokpitTheme(dark = dark, accent = accent) { DesignScale { HomeScreen(state(dark), now, HomeActions(car = io.github.yorulmazsinan.kokpit.home.cards.CarShortcuts()), window = window) } } }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun home1024x600() = capture("home-1024x600")

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun home1024x600Light() = capture("home-1024x600-light", dark = false)

    @Test @Config(qualifiers = "tr-w800dp-h480dp-land-mdpi")
    fun home800x480() = capture("home-800x480")

    @Test @Config(qualifiers = "tr-w1280dp-h720dp-land-mdpi")
    fun home1280x720() = capture("home-1280x720")

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun accentGaugeGreen() = capture("accent-gauge_green", accent = ThemeAccent.GAUGE_GREEN)

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun accentChrome() = capture("accent-chrome", accent = ThemeAccent.CHROME)

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun accentNightBlue() = capture("accent-night_blue", accent = ThemeAccent.NIGHT_BLUE)

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saab1024x600() = capture("saab-1024x600", accent = ThemeAccent.SAAB_93)

    @Test @Config(qualifiers = "tr-w800dp-h480dp-land-mdpi")
    fun saab800x480() = capture("saab-800x480", accent = ThemeAccent.SAAB_93)

    @Test @Config(qualifiers = "tr-w1280dp-h720dp-land-mdpi")
    fun saab1280x720() = capture("saab-1280x720", accent = ThemeAccent.SAAB_93)

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabNightPanel() {
        compose.setContent { KokpitTheme(accent = ThemeAccent.SAAB_93) { DesignScale { NightPanel {} } } }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screenshots/saab-night-panel.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabBrightness() {
        compose.setContent {
            KokpitTheme(accent = ThemeAccent.SAAB_93) {
                DesignScale {
                    androidx.compose.foundation.layout.Box {
                        HomeScreen(state(true), now, HomeActions())
                        BrightnessPanel(onDismiss = {}, initial = 140, canWriteOverride = true)
                    }
                }
            }
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screenshots/saab-brightness.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabFuel() {
        val car = io.github.yorulmazsinan.kokpit.car.CarRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        listOf(
            io.github.yorulmazsinan.kokpit.car.FuelEntry(1_790_000_000_000, 84_120, 52.4, 2_410.0),
            io.github.yorulmazsinan.kokpit.car.FuelEntry(1_791_000_000_000, 84_790, 48.1, 2_250.5),
            io.github.yorulmazsinan.kokpit.car.FuelEntry(System.currentTimeMillis(), 85_420, 45.3, 2_160.0),
        ).forEach(car::addFuel)
        shot("saab-fuel") { io.github.yorulmazsinan.kokpit.car.FuelScreen(car) {} }
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabParking() {
        val car = io.github.yorulmazsinan.kokpit.car.CarRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        car.park(41.0082, 28.9784, System.currentTimeMillis() - 3_600_000)
        shot("saab-parking") { io.github.yorulmazsinan.kokpit.car.ParkingScreen(car, { 41.0122 to 28.9760 }) {} }
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabIdle() = shot("saab-idle") {
        io.github.yorulmazsinan.kokpit.home.cards.IdleScreen(9.4, onExit = {}, fixedNow = java.time.LocalDateTime.of(2026, 10, 31, 21, 47))
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabTrips() {
        val log = io.github.yorulmazsinan.kokpit.car.TripLog(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val now = System.currentTimeMillis()
        log.begin(0.0, now - 5 * 3_600_000); log.end(18_400.0, now - 5 * 3_600_000 + 26 * 60_000)
        log.begin(0.0, now - 2 * 3_600_000); log.end(42_700.0, now - 2 * 3_600_000 + 58 * 60_000)
        val car = io.github.yorulmazsinan.kokpit.car.CarRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        shot("saab-trips") { io.github.yorulmazsinan.kokpit.car.LogbookScreen(car, log, io.github.yorulmazsinan.kokpit.car.ReminderStore(androidx.test.core.app.ApplicationProvider.getApplicationContext()), onBack = {}, initial = io.github.yorulmazsinan.kokpit.car.LogbookTab.TRIPS) }
    }

    @Test @Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
    fun saabPortraitPhoto() {
        val bmp = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 360f, 640f, intArrayOf(0xFF3A6EA5.toInt(), 0xFFF2C14E.toInt(), 0xFF7A3B2E.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 360f, 640f, paint)
        val tiny = Bitmap.createScaledBitmap(bmp, 24, 43, true)
        val window = io.github.yorulmazsinan.kokpit.home.cards.WindowCardState(bmp.asImageBitmap(), "Chrome", null, tiny.asImageBitmap(), portrait = true)
        compose.setContent { KokpitTheme(accent = ThemeAccent.SAAB_93) { DesignScale { HomeScreen(state(true), now, HomeActions(car = io.github.yorulmazsinan.kokpit.home.cards.CarShortcuts()), window = window) } } }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screenshots/saab-portrait.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { KokpitTheme(accent = ThemeAccent.SAAB_93) { DesignScale { content() } } }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    @Test @Config(qualifiers = "tr-w683dp-h360dp-land-hdpi")
    fun home683x360() = capture("home-683x360")

    @Test @Config(qualifiers = "tr-w768dp-h410dp-land-tvdpi")
    fun home768x410() = capture("home-768x410")
}
