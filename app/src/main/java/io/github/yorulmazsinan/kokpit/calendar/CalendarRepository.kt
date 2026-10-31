package io.github.yorulmazsinan.kokpit.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CalendarState {
    data object PermissionMissing : CalendarState
    data object Empty : CalendarState
    data class Next(val event: UpcomingEvent) : CalendarState
}

/** Reads whatever calendars are synced on the device (Google, or iCloud through DAVx⁵). */
class CalendarRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<CalendarState>(CalendarState.Empty)
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            while (isActive) {
                refresh()
                delay(60_000)
            }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch { refresh() }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        }
    }

    suspend fun refresh() {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            _state.value = CalendarState.PermissionMissing
            return
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val instances = withContext(Dispatchers.IO) { queryInstances(startOfDay, endOfDay) }
        _state.value = pickNextEvent(instances, now, endOfDay)?.let(CalendarState::Next) ?: CalendarState.Empty
    }

    private fun queryInstances(begin: Long, end: Long): List<EventInstance> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(EventInstance(c.getString(0).orEmpty(), c.getLong(1), c.getLong(2), c.getInt(3) == 1))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
