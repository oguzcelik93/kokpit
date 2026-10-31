package io.github.yorulmazsinan.kokpit.home

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import io.github.yorulmazsinan.kokpit.KokpitApp
import androidx.compose.foundation.layout.Box
import io.github.yorulmazsinan.kokpit.assistant.AssistantPanel
import io.github.yorulmazsinan.kokpit.assistant.AssistantState
import io.github.yorulmazsinan.kokpit.assistant.SetupProblem
import io.github.yorulmazsinan.kokpit.calls.ActiveCall
import io.github.yorulmazsinan.kokpit.calls.CallBar
import io.github.yorulmazsinan.kokpit.calls.CallEntry
import io.github.yorulmazsinan.kokpit.calls.CallPhase
import io.github.yorulmazsinan.kokpit.player.MediaFile
import io.github.yorulmazsinan.kokpit.player.MediaKind
import io.github.yorulmazsinan.kokpit.player.PlayerContent
import io.github.yorulmazsinan.kokpit.player.PlayerState
import io.github.yorulmazsinan.kokpit.settings.QuickSettingsActions
import io.github.yorulmazsinan.kokpit.settings.QuickSettingsContent
import io.github.yorulmazsinan.kokpit.settings.QuickSettingsState
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.drawer.AppDrawerScreen
import io.github.yorulmazsinan.kokpit.home.cards.Dock
import io.github.yorulmazsinan.kokpit.intro.IntroSettingsScreen
import io.github.yorulmazsinan.kokpit.settings.SettingsScreen
import io.github.yorulmazsinan.kokpit.ui.components.DockedLayout
import io.github.yorulmazsinan.kokpit.calls.CallType
import io.github.yorulmazsinan.kokpit.media.NowPlaying
import io.github.yorulmazsinan.kokpit.media.NowPlayingState
import io.github.yorulmazsinan.kokpit.music.BtMusicScreen
import io.github.yorulmazsinan.kokpit.phone.Contact
import io.github.yorulmazsinan.kokpit.phone.PhoneContent
import io.github.yorulmazsinan.kokpit.radio.RadioPreset
import io.github.yorulmazsinan.kokpit.radio.RadioScreen
import io.github.yorulmazsinan.kokpit.ui.theme.DesignScale
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w683dp-h400dp-land-hdpi")
class ScreensScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun docked(active: DockSlot, content: @Composable () -> Unit) =
        DockedLayout({ Dock({}, {}, active = active) }, content = content)

    private val container get() = ApplicationProvider.getApplicationContext<KokpitApp>().container

    @Test
    fun settings() {
        compose.setContent { KokpitTheme { DesignScale { SettingsScreen(container, {}, {}, {}, {}, {}) } } }
        save("screen-settings")
    }

    @Test
    fun drawer() {
        compose.setContent { KokpitTheme { DesignScale { AppDrawerScreen(container.installedApps, onBack = {}, onOpenSettings = {}) } } }
        save("screen-drawer")
    }

    @Test
    fun intro() {
        compose.setContent { KokpitTheme { DesignScale { IntroSettingsScreen(container, onBack = {}) } } }
        save("screen-intro")
    }

    @Test
    fun quickSettings() {
        val state = QuickSettingsState(muted = false, brightness = 180, canSetBrightness = true, wifiNetwork = "Sinan iPhone", hotspot = "Sinan iPhone")
        compose.setContent { KokpitTheme { DesignScale { QuickSettingsContent(state, QuickSettingsActions(), onBack = {}) } } }
        save("screen-quick")
    }

    @Test
    fun player() {
        val files = listOf(
            MediaFile("/u/a.mp3", "Gesi Bağları", "Barış Manço", MediaKind.AUDIO),
            MediaFile("/u/b.mp3", "Dönence", "Barış Manço", MediaKind.AUDIO),
            MediaFile("/u/c.mp3", "Kara Sevda", "Cem Karaca", MediaKind.AUDIO),
        )
        compose.setContent {
            KokpitTheme {
                DesignScale {
                    DockedLayout({ Dock({}, {}) }) {
                        PlayerContent(files, PlayerState(files[0], isPlaying = true, hasNext = true), false, 84_000, 221_000, { _, _ -> }, {}, {}, {}, {})
                    }
                }
            }
        }
        save("screen-player")
    }

    @Test
    fun playerVideoWhileMoving() {
        val video = MediaFile("/u/v.mp4", "Konser", "Videolar", MediaKind.VIDEO)
        compose.setContent {
            KokpitTheme { DesignScale { PlayerContent(listOf(video), PlayerState(video, isPlaying = true), true, 10_000, 600_000, { _, _ -> }, {}, {}, {}, {}) } }
        }
        save("screen-player-moving")
    }

    @Test
    fun incomingCallOverPhone() {
        compose.setContent {
            KokpitTheme {
                DesignScale {
                    DockedLayout(
                        { Dock({}, {}, active = DockSlot.RADIO) },
                        { CallBar(ActiveCall(CallPhase.INCOMING, "05551112233", "Ece", null), {}, {}, {}) },
                    ) { RadioScreen(984, SAMPLE_PRESETS, "Kral FM", {}, {}, {}, {}, connected = true) }
                }
            }
        }
        save("screen-call")
    }

    @Test
    fun assistantListening() = assistant("screen-assistant-listening", AssistantState.Listening("Annemi ara"))

    @Test
    fun assistantAnswer() = assistant(
        "screen-assistant-answer",
        AssistantState.Answer("Yarın İstanbul'da hava nasıl?", "Yarın İstanbul'da parçalı bulutlu, en yüksek 21, en düşük 13 derece bekleniyor.", speaking = true),
    )

    @Test
    fun assistantSetup() = assistant("screen-assistant-setup", AssistantState.NeedsSetup(SetupProblem.NO_KEY))

    private fun assistant(name: String, state: AssistantState) {
        compose.setContent {
            KokpitTheme {
                DesignScale {
                    Box {
                        DockedLayout({ Dock({}, {}, active = DockSlot.RADIO) }) { RadioScreen(984, SAMPLE_PRESETS, "Kral FM", {}, {}, {}, {}, connected = true) }
                        AssistantPanel(state, "Claude · claude-opus-5-5", {}, {}, {}, {})
                    }
                }
            }
        }
        save(name)
    }

    @Test
    fun phone() {
        val calls = listOf(
            CallEntry("Ece", "0555", CallType.MISSED, 1_798_000_000_000L),
            CallEntry("Ofis", "0212", CallType.OUTGOING, 1_797_900_000_000L),
            CallEntry(null, "0532 111 22 33", CallType.INCOMING, 1_797_800_000_000L),
            CallEntry("Annem", "0533", CallType.INCOMING, 1_797_700_000_000L),
        )
        compose.setContent { KokpitTheme { DesignScale { docked(DockSlot.PHONE) { PhoneContent(calls, listOf(Contact("Annem", "0533", true)), onBack = {}, onDial = {}, deviceName = "iPhone") } } } }
        save("screen-phone")
    }

    @Test
    fun music() {
        val np = NowPlaying("Gesi Bağları", "Barış Manço", "Bluetooth", null, isPlaying = true, durationMs = 221_000, positionMs = 84_000)
        compose.setContent { KokpitTheme { DesignScale { docked(DockSlot.BLUETOOTH) { BtMusicScreen(NowPlayingState.Playing(np), {}, {}, {}, {}, {}) } } } }
        save("screen-music")
    }

    @Test
    fun radio() {
        compose.setContent { KokpitTheme { DesignScale { docked(DockSlot.RADIO) { RadioScreen(984, SAMPLE_PRESETS, "Kral FM", {}, {}, {}, {}, connected = true) } } } }
        save("screen-radio")
    }
}

private val SAMPLE_PRESETS = listOf(
    RadioPreset(984, "Kral FM"),
    RadioPreset(914, "TRT FM"),
    RadioPreset(953, "Power FM"),
    RadioPreset(1040, "Radyo Eksen"),
    RadioPreset(974),
    null,
)
