package io.github.yorulmazsinan.kokpit.assistant

import android.content.Context
import android.content.Intent
import io.github.yorulmazsinan.kokpit.car.PlaceKind
import io.github.yorulmazsinan.kokpit.car.navigateTo
import io.github.yorulmazsinan.kokpit.car.place
import io.github.yorulmazsinan.kokpit.car.savedPlaceFor
import android.net.Uri
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.calendar.CalendarState
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.dock.parseDockTarget
import io.github.yorulmazsinan.kokpit.drive.CompassPoint
import io.github.yorulmazsinan.kokpit.headunit.usesHeadUnitMusic
import io.github.yorulmazsinan.kokpit.media.NowPlayingState
import io.github.yorulmazsinan.kokpit.phone.Contact
import io.github.yorulmazsinan.kokpit.phone.loadContacts
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.weather.WeatherCondition
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Kokpit screens the assistant can open by name (navigation routes). */
interface AssistantNavigator {
    fun openRoute(route: String)
    fun openSlot(slot: DockSlot)
}

/** Screen and app names as people say them, mapped to Kokpit screens or dock slots. */
private val SCREEN_WORDS: List<Pair<List<String>, Any>> = listOf(
    listOf("carplay", "car play", "karplay") to DockSlot.CARPLAY,
    listOf("radyo", "radio", "fm") to DockSlot.RADIO,
    listOf("bluetooth", "muzik", "music", "sarki") to DockSlot.BLUETOOTH,
    listOf("telefon", "rehber", "tus takimi", "phone") to DockSlot.PHONE,
    listOf("harita", "navigasyon", "maps", "yol tarifi") to DockSlot.MAPS,
    listOf("usb", "medya", "video", "flash") to "player",
    listOf("hizli ayar") to "quick",
    listOf("ayar", "settings") to "settings",
    listOf("ana ekran", "anasayfa", "ana sayfa", "home", "kokpit") to "home",
    listOf("uygulamalar", "uygulama listesi", "apps") to "drawer",
)

fun screenFor(name: String): Any? {
    val folded = foldTurkish(name)
    return SCREEN_WORDS.firstOrNull { (words, _) -> words.any { folded.contains(it) } }?.second
}

class DeviceActions(
    private val context: Context,
    private val container: AppContainer,
    private val navigator: () -> AssistantNavigator?,
) : AssistantActions {

    override fun openApp(name: String): String {
        val nav = navigator()
        when (val screen = screenFor(name)) {
            is DockSlot -> nav?.openSlot(screen)?.let { return "Opened ${screen.name.lowercase()}." }
            is String -> nav?.openRoute(screen)?.let { return "Opened $screen." }
        }
        val folded = foldTurkish(name)
        val apps = container.installedApps.launchable()
        val app = apps.firstOrNull { foldTurkish(it.label) == folded }
            ?: apps.firstOrNull { foldTurkish(it.label).contains(folded) || folded.contains(foldTurkish(it.label)) }
            ?: return "Error: no app called \"$name\". Installed apps: ${apps.joinToString { it.label }.take(600)}"
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return "Error: ${app.label} cannot be opened."
        return if (safeStart(context, intent)) "Opened ${app.label}." else "Error: ${app.label} did not open."
    }

    override fun media(action: String): String {
        val usb = container.usbPlayer.state.value
        if (usb.current != null) {
            when (action) {
                "play" -> if (!usb.isPlaying) container.usbPlayer.playPause()
                "pause" -> if (usb.isPlaying) container.usbPlayer.playPause()
                "next" -> container.usbPlayer.next()
                "previous" -> container.usbPlayer.previous()
                else -> return "Error: unknown action $action."
            }
            return "Done ($action, USB media)."
        }
        val now = container.nowPlaying.state.value as? NowPlayingState.Playing
        val vendor = container.headUnit.phone
        // Bluetooth music: the head unit knows whether it is really playing; the phone's session does not.
        val headUnitPlaying = if (usesHeadUnitMusic(container.nowPlaying.currentPackage())) container.btMusic.isPlaying() else null
        val playing = headUnitPlaying ?: (now?.nowPlaying?.isPlaying == true)
        val viaHeadUnit = headUnitPlaying != null || now == null
        when (action) {
            "play" -> if (!playing) { if (viaHeadUnit) vendor?.play() ?: return "Error: nothing to play." else container.nowPlaying.playPause() }
            "pause" -> if (playing) { if (viaHeadUnit) vendor?.pause() else container.nowPlaying.playPause() }
            "next" -> if (now != null) container.nowPlaying.next() else vendor?.next() ?: return "Error: nothing is playing."
            "previous" -> if (now != null) container.nowPlaying.previous() else vendor?.previous() ?: return "Error: nothing is playing."
            else -> return "Error: unknown action $action."
        }
        return "Done ($action${now?.nowPlaying?.sourceLabel?.let { ", $it" }.orEmpty()})."
    }

    override fun navigate(destination: String): String {
        if (destination.isBlank()) return "Error: no destination given."
        savedPlaceFor(destination)?.let { kind ->
            val name = if (kind == PlaceKind.HOME) "home" else "work"
            if (!container.car.place(kind).isSet) {
                return "Error: the driver's $name is not saved yet. Tell them: long-press the ${if (kind == PlaceKind.HOME) "Ev" else "İş"} button on the home screen while there, or type the address in Settings → Araç → Ev ve İş."
            }
            navigateTo(context, container, kind, container.dock.targets.value[DockSlot.MAPS])
            return "Navigation to the driver's saved $name started."
        }
        val q = Uri.encode(destination)
        val chosen = container.dock.targets.value[DockSlot.MAPS]?.let(::parseDockTarget)?.packageName
        val installed = container.installedApps.packages()
        val candidates = buildList {
            if (chosen == YANDEX || (chosen == null && YANDEX in installed)) add(Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://map_search?text=$q")).setPackage(YANDEX))
            if (GOOGLE_MAPS in installed) add(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(GOOGLE_MAPS))
            if (YANDEX in installed) add(Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://map_search?text=$q")).setPackage(YANDEX))
            add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
        }
        val started = candidates.firstOrNull { safeStart(context, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ?: return "Error: no maps app could open the route."
        val app = if (started.`package` == YANDEX) "Yandex Navigasyon" else if (started.`package` == GOOGLE_MAPS) "Google Haritalar" else "the maps app"
        return "Navigation to \"$destination\" opened in $app."
    }

    override fun tuneRadio(tenths: Int): String {
        val radio = container.headUnit.radio ?: return "Error: the unit's radio service is not available."
        navigator()?.openSlot(DockSlot.RADIO)
        val ok = radio.tune(tenths)
        container.scope.launch { container.settings.update { it.copy(radioFrequency = tenths) } }
        return if (ok) "Radio tuned to ${tenths / 10}.${tenths % 10} FM." else "Error: the radio did not accept the frequency."
    }

    override fun call(query: String): String {
        val phone = container.headUnit.phone ?: return "Error: the unit's phone service is not available."
        if (phone.deviceName().isNullOrBlank() && container.headUnit.common?.bluetoothConnected() == false) {
            return "Error: no phone is connected over Bluetooth."
        }
        val number = spokenNumber(query)
        if (number != null) return if (phone.dial(number)) "Calling $number." else "Error: the call could not be placed."
        val fromCalls = container.calls.calls.value.mapNotNull { c -> c.name?.takeIf { it.isNotBlank() }?.let { Contact(it, c.number, false) } }
        val matches = matchContacts(query, loadContacts(context) + fromCalls)
        return when {
            matches.isEmpty() -> "Error: no contact called \"$query\"."
            matches.size > 1 && foldTurkish(matches[0].name) != foldTurkish(query) ->
                "Several contacts match: ${matches.take(5).joinToString { it.name }}. Ask the driver which one; do not call yet."
            phone.dial(matches[0].number) -> "Calling ${matches[0].name}."
            else -> "Error: the call could not be placed."
        }
    }

    override fun volume(action: String): String {
        val common = container.headUnit.common ?: return "Error: volume control is not available on this unit."
        val ok = when (action) {
            "up" -> common.volumeUp()
            "down" -> common.volumeDown()
            "mute" -> common.mute()
            else -> return "Error: unknown action $action."
        }
        return if (ok) "Volume: $action." else "Error: the unit did not respond."
    }

    override fun status(): String {
        val tr = Locale.forLanguageTag("tr-TR")
        val now = io.github.yorulmazsinan.kokpit.time.TrueTime.dateTime()
        val trip = container.drive.state.value
        return buildList {
            add("Saat ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}, ${now.format(DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", tr))}.")
            add(trip.speedKmh?.let { "Hız $it km/s." } ?: "Hız bilinmiyor (GPS yok).")
            trip.averageKmh?.let { add("Ortalama hız $it km/s.") }
            trip.bearingDeg?.let { add("Yön ${context.getString(CompassPoint.fromBearing(it).fullLabel)} (${it.toInt()}°).") }
            when (val w = container.weather.state.value) {
                is WeatherState.Fresh, is WeatherState.Stale -> {
                    val weather = if (w is WeatherState.Fresh) w.weather else (w as WeatherState.Stale).weather
                    add("Hava ${weather.temperatureC.toInt()}°C, ${context.getString(WeatherCondition.fromWmo(weather.code).label)}, en yüksek ${weather.maxC.toInt()}°, en düşük ${weather.minC.toInt()}°.")
                }
                is WeatherState.NoData -> add("Hava durumu yok (internet ya da konum yok).")
            }
            (container.calendar.state.value as? CalendarState.Next)?.event?.let { e ->
                val at = Instant.ofEpochMilli(e.beginMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM HH:mm", tr))
                add("Sıradaki etkinlik: ${e.title}, $at.")
            }
            (container.nowPlaying.state.value as? NowPlayingState.Playing)?.nowPlaying?.let { np ->
                add("${if (np.isPlaying) "Çalıyor" else "Duraklatıldı"}: ${np.title}${np.artist?.let { " — $it" }.orEmpty()} (${np.sourceLabel}).")
            }
            container.usbPlayer.state.value.current?.let { add("USB medya: ${it.name}.") }
            container.headUnit.frequency.value?.let { add("Radyo ${it / 10}.${it % 10} FM.") }
            add(container.headUnit.btDevice.value?.let { "Bağlı telefon: $it." } ?: "Bluetooth ile bağlı telefon yok.")
        }.joinToString(" ")
    }

    override fun connectPhoneWifi(): String {
        val ssid = container.settings.settings.value.hotspotSsid ?: return "Error: no phone hotspot is saved in Kokpit settings."
        container.wifi.reconnect(container.scope)
        return "Connecting to the hotspot \"$ssid\"."
    }

    private companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        const val YANDEX = "ru.yandex.yandexnavi"
    }
}
