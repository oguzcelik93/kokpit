package io.github.yorulmazsinan.kokpit.home

import io.github.yorulmazsinan.kokpit.calendar.CalendarState
import io.github.yorulmazsinan.kokpit.calls.CallEntry
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.drive.TripSnapshot
import io.github.yorulmazsinan.kokpit.media.NowPlayingState
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import io.github.yorulmazsinan.kokpit.weather.WeatherState

enum class ThemeMode { DARK, LIGHT, AUTO }

data class HomeUiState(
    val weather: WeatherState,
    val calendar: CalendarState,
    val media: NowPlayingState,
    val trip: TripSnapshot,
    val locationPermissionMissing: Boolean,
    val dock: Map<DockSlot, String?>,
    val dark: Boolean,
    val accent: ThemeAccent = ThemeAccent.SAAB_93,
    val recentCalls: List<CallEntry> = emptyList(),
)

class HomeActions(
    val onDock: (DockSlot) -> Unit = {},
    val onDockLongPress: (DockSlot) -> Unit = {},
    val onAssistant: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onRefreshWeather: () -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onOpenMaps: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onRequestPermissions: () -> Unit = {},
    val onGrantMediaAccess: () -> Unit = {},
    val onNightPanel: (() -> Unit)? = null,
    /** Hidden recovery entry: long-press on the clock. */
    val onRecovery: () -> Unit = {},
    val onBrightness: () -> Unit = {},
    val car: io.github.yorulmazsinan.kokpit.home.cards.CarShortcuts? = null,
)
