package io.github.yorulmazsinan.kokpit.home

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.calendar.CalendarState
import io.github.yorulmazsinan.kokpit.calls.CallEntry
import io.github.yorulmazsinan.kokpit.calls.CallType
import io.github.yorulmazsinan.kokpit.drive.CompassPoint
import io.github.yorulmazsinan.kokpit.calendar.UpcomingEvent
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.home.cards.Dock
import io.github.yorulmazsinan.kokpit.drive.TripSnapshot
import io.github.yorulmazsinan.kokpit.media.NowPlayingState
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitTheme
import io.github.yorulmazsinan.kokpit.weather.Weather
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1024dp-h600dp-land-mdpi")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = LocalDateTime.of(2026, 10, 27, 15, 2)

    private val weather = Weather(
        temperatureC = 18.4, code = 2, isDay = true, maxC = 21.0, minC = 12.1,
        sunriseLocal = LocalTime.of(6, 58), sunsetLocal = LocalTime.of(17, 21),
        fetchedAtEpochMs = LocalDateTime.of(2026, 10, 27, 14, 10).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    private val base = HomeUiState(
        weather = WeatherState.Fresh(weather),
        calendar = CalendarState.Next(UpcomingEvent("Client meeting", LocalDateTime.of(2026, 10, 27, 16, 30).toInstant(ZoneOffset.UTC).toEpochMilli())),
        media = NowPlayingState.Idle,
        trip = TripSnapshot(speedKmh = 62, compass = null, averageKmh = 48, hasFix = true),
        locationPermissionMissing = false,
        dock = DockSlot.entries.associateWith { null },
        dark = true,
    )

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    private fun show(state: HomeUiState) {
        compose.setContent { KokpitTheme { HomeScreen(state, now, HomeActions()) } }
    }

    @Test
    fun showsCachedWeatherWithTimestampWhenStale() {
        show(base.copy(weather = WeatherState.Stale(weather)))
        compose.onNodeWithText(context.getString(R.string.weather_updated_at, "14:10"), substring = true).assertExists()
    }

    @Test
    fun showsInternetNeededWhenNoWeatherData() {
        show(base.copy(weather = WeatherState.NoData(io.github.yorulmazsinan.kokpit.weather.WeatherProblem.NO_NETWORK)))
        compose.onNodeWithText(context.getString(R.string.weather_needs_internet)).assertExists()
    }

    @Test
    fun saysWaitingForLocationAndRefreshesOnTap() {
        var refreshed = false
        compose.setContent {
            KokpitTheme {
                HomeScreen(
                    base.copy(weather = WeatherState.NoData(io.github.yorulmazsinan.kokpit.weather.WeatherProblem.NO_LOCATION)),
                    now,
                    HomeActions(onRefreshWeather = { refreshed = true }),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.weather_waiting_location)).assertExists().performClick()
        assertTrue(refreshed)
    }

    @Test
    fun hidesCalendarRowWhenEmpty() {
        show(base.copy(calendar = CalendarState.Empty))
        compose.onNodeWithTag("calendar_row").assertDoesNotExist()
    }

    @Test
    fun showsPermissionPromptWhenCalendarPermissionMissing() {
        show(base.copy(calendar = CalendarState.PermissionMissing))
        compose.onNodeWithText(context.getString(R.string.calendar_permission)).assertExists()
    }

    @Test
    fun showsGpsSearchingWithoutFix() {
        show(base.copy(trip = TripSnapshot(null, null, null, hasFix = false)))
        compose.onNodeWithText(context.getString(R.string.gps_searching)).assertExists()
    }

    @Test
    fun showsNothingPlayingWhenIdle() {
        show(base)
        compose.onNodeWithText(context.getString(R.string.nothing_playing)).assertExists()
    }

    @Test
    fun dockShowsSixButtons() {
        show(base)
        DockSlot.entries.filter { it.inDock }.forEach { slot ->
            compose.onNodeWithTag("dock_${slot.name}").assertExists().assertHeightIsAtLeast(64.dp)
        }
        compose.onNodeWithTag("dock_${DockSlot.ASSISTANT.name}").assertDoesNotExist()
    }

    @Test
    fun onlyTheActiveDockButtonIsLit() {
        compose.setContent { KokpitTheme { Dock({}, {}, active = DockSlot.PHONE) } }
        compose.onNodeWithTag("dock_PHONE").assertIsSelected()
        compose.onNodeWithTag("dock_CARPLAY").assertIsNotSelected()
    }

    @Test
    fun settingsButtonIsVisibleAndOpensSettings() {
        var opened = false
        compose.setContent { KokpitTheme { HomeScreen(base, now, HomeActions(onOpenSettings = { opened = true })) } }
        compose.onNodeWithTag("home_settings").assertExists().assertHeightIsAtLeast(64.dp).performClick()
        assertTrue(opened)
    }

    @Test
    fun speedCardShowsSmallAverageAboveGauge() {
        show(base)
        compose.onNodeWithTag("speed_value", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("average_speed", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.average_speed_short, 48), useUnmergedTree = true).assertExists()
    }

    @Test
    fun noAverageBeforeItIsKnown() {
        show(base.copy(trip = base.trip.copy(averageKmh = null)))
        compose.onNodeWithTag("average_speed", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun showsRecentCallsWhenAvailable() {
        show(base.copy(recentCalls = listOf(CallEntry("Ece", "0555", CallType.MISSED, 0L))))
        compose.onNodeWithTag("recent_calls").assertExists()
        compose.onNodeWithText("Ece", substring = true).assertExists()
    }

    @Test
    fun hidesRecentCallsWhenNone() {
        show(base)
        compose.onNodeWithTag("recent_calls").assertDoesNotExist()
    }

    @Test
    fun compassCardShowsFullHeadingName() {
        show(base.copy(trip = TripSnapshot(speedKmh = 62, compass = CompassPoint.NW, averageKmh = null, hasFix = true, bearingDeg = 315f)))
        compose.onNodeWithTag("compass_card").assertExists()
        compose.onNodeWithText(context.getString(R.string.compass_full_nw)).assertExists()
        compose.onNodeWithText("315°").assertExists()
    }

    @Test
    fun musicCardSeparatesInfoFromControls() {
        show(base.copy(media = NowPlayingState.Playing(io.github.yorulmazsinan.kokpit.media.NowPlaying("Şarkı", "Sanatçı", "Spotify", null, true))))
        compose.onNodeWithTag("music_divider").assertExists()
    }
}
