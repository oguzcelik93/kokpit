package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Dated things the car needs; [key] is what the panel and the store use. */
enum class ReminderKind(val key: String, val label: String) {
    MUAYENE("muayene", "Muayene"),
    SIGORTA("sigorta", "Trafik sigortası"),
    KASKO("kasko", "Kasko"),
    YAG("yag", "Yağ değişimi"),
}

/**
 * What the owner entered: a due date per kind, the oil change's km (from the fuel log's
 * odometer), and whether to remind of winter/summer tyres.
 */
@Serializable
data class Reminders(val dates: Map<String, String> = emptyMap(), val oilKm: Int? = null, val tires: Boolean = false) {
    fun date(kind: ReminderKind): LocalDate? = dates[kind.key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** One line to show: [overdue] ones in red. */
data class DueReminder(val text: String, val overdue: Boolean)

/** Remind this many days before a date … */
const val REMIND_DAYS = 30

/** … and this many km before the oil change. */
const val REMIND_KM = 1_000

private val SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * What is due soon, most urgent first. Tyres: winter ones from 1 November to 1 December
 * (the legal season starts 1 December), summer ones from 1 April to 1 May.
 */
fun dueReminders(r: Reminders, today: LocalDate, odometerKm: Int?): List<DueReminder> {
    val out = mutableListOf<Pair<Long, DueReminder>>()
    for (kind in ReminderKind.entries) {
        val date = r.date(kind) ?: continue
        val days = ChronoUnit.DAYS.between(today, date)
        if (days > REMIND_DAYS) continue
        val text = when {
            days < 0 -> "${kind.label} ${-days} gün geçti (${date.format(SHORT)})"
            days == 0L -> "${kind.label} bugün"
            else -> "${kind.label} $days gün sonra (${date.format(SHORT)})"
        }
        out += days to DueReminder(text, days < 0)
    }
    val oil = r.oilKm
    if (oil != null && odometerKm != null) {
        val left = oil - odometerKm
        if (left <= REMIND_KM) {
            val text = if (left < 0) "Yağ değişimi ${-left} km geçti" else "Yağ değişimine $left km kaldı"
            // Weigh km like days (≈ 50 km a day) so the list stays in one order.
            out += (left / 50L) to DueReminder(text, left < 0)
        }
    }
    if (r.tires) {
        val md = MonthDay.from(today)
        when {
            !md.isBefore(MonthDay.of(11, 1)) && md.isBefore(MonthDay.of(12, 1)) -> out += 10L to DueReminder("Kışlık lastik zamanı", false)
            !md.isBefore(MonthDay.of(4, 1)) && md.isBefore(MonthDay.of(5, 1)) -> out += 10L to DueReminder("Yazlık lastik zamanı", false)
        }
    }
    return out.sortedBy { it.first }.map { it.second }
}

/**
 * `{"muayene":"2027-03-01","sigorta":"","oilKm":"95000","tires":true}` → the new reminders
 * (only the keys given change; an empty value clears). Null — nothing changes — when any key
 * is unknown or any value is not a date / km.
 */
fun applyReminders(current: Reminders, body: String): Reminders? = runCatching {
    val o = Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return null
    var dates = current.dates
    var oil = current.oilKm
    var tires = current.tires
    val keys = ReminderKind.entries.associateBy { it.key }
    for ((k, v) in o) {
        val p = v as? kotlinx.serialization.json.JsonPrimitive ?: return null
        when {
            k in keys -> {
                val text = p.content.trim()
                dates = if (text.isEmpty()) dates - k else {
                    val d = LocalDate.parse(text)
                    if (d.year !in 2000..2100) return null
                    dates + (k to d.toString())
                }
            }
            k == "oilKm" -> oil = p.content.trim().let { t -> if (t.isEmpty()) null else t.toInt().takeIf { it in 1..2_000_000 } ?: return null }
            k == "tires" -> tires = p.content.toBooleanStrictOrNull() ?: return null
            else -> return null
        }
    }
    Reminders(dates, oil, tires)
}.getOrNull()

/** The owner's reminders, kept with the car (they don't change with the driver). */
class ReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _reminders = MutableStateFlow(
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(Reminders.serializer(), it) }.getOrNull() } ?: Reminders(),
    )
    val reminders: StateFlow<Reminders> = _reminders.asStateFlow()

    fun set(r: Reminders) {
        prefs.edit().putString(KEY, json.encodeToString(Reminders.serializer(), r)).apply()
        _reminders.value = r
    }

    /** The home screen's reminder is shown once a day, until opened. */
    fun seenToday(today: LocalDate): Boolean = prefs.getString(KEY_SEEN, null) == today.toString()
    fun markSeen(today: LocalDate) = prefs.edit().putString(KEY_SEEN, today.toString()).apply()

    private companion object {
        const val KEY = "reminders"
        const val KEY_SEEN = "seen"
    }
}
