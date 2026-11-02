package io.github.yorulmazsinan.kokpit.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.home.cards.ClockCard
import io.github.yorulmazsinan.kokpit.home.cards.Dock
import io.github.yorulmazsinan.kokpit.home.cards.CompassCard
import io.github.yorulmazsinan.kokpit.home.cards.WindowCard
import io.github.yorulmazsinan.kokpit.home.cards.WindowCardState
import io.github.yorulmazsinan.kokpit.home.cards.NowPlayingCard
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import java.time.LocalDateTime

@Composable
fun HomeScreen(state: HomeUiState, now: LocalDateTime, actions: HomeActions, showDock: Boolean = true, window: WindowCardState? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            val clock = @Composable { m: Modifier ->
                ClockCard(
                    now = now,
                    weather = state.weather,
                    calendar = state.calendar,
                    recentCalls = state.recentCalls,
                    onOpenPhone = { actions.onDock(DockSlot.PHONE) },
                    onAssistant = actions.onAssistant,
                    onOpenSettings = actions.onOpenSettings,
                    onRefreshWeather = actions.onRefreshWeather,
                    onOpenCalendar = actions.onOpenCalendar,
                    onRequestPermissions = actions.onRequestPermissions,
                    onRecovery = actions.onRecovery,
                    onBrightness = actions.onBrightness,
                    modifier = m,
                )
            }
            val media = @Composable { m: Modifier ->
                NowPlayingCard(
                    state = state.media,
                    onOpenPlayer = { actions.onDock(DockSlot.BLUETOOTH) },
                    onGrantAccess = actions.onGrantMediaAccess,
                    onPrevious = actions.onPrevious,
                    onPlayPause = actions.onPlayPause,
                    onNext = actions.onNext,
                    modifier = m,
                )
            }
            val photo = @Composable { m: Modifier ->
                WindowCard(state = window, onOpen = actions.onWindowOpen, onSettings = actions.onWindowSettings, modifier = m, onSize = actions.onWindowSize)
            }
            if (window?.portrait == true && window.image != null) {
                // A portrait photo gets a tall frame on the right; music and the heading strip
                // move into the middle column.
                clock(Modifier.weight(1.1f).fillMaxHeight())
                Column(Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    media(Modifier.weight(1.4f).fillMaxWidth())
                    CompassCard(state.trip, Modifier.weight(0.6f).fillMaxWidth(), onClick = actions.onOpenMaps, shortcuts = actions.car)
                }
                photo(Modifier.weight(0.8f).fillMaxHeight())
            } else {
                clock(Modifier.weight(1.25f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    media(Modifier.weight(0.95f).fillMaxWidth())
                    photo(Modifier.weight(1.45f).fillMaxWidth())
                    CompassCard(state.trip, Modifier.weight(0.6f).fillMaxWidth(), onClick = actions.onOpenMaps, shortcuts = actions.car)
                }
            }
        }
        if (showDock) Dock(actions.onDock, actions.onDockLongPress)
    }
}
