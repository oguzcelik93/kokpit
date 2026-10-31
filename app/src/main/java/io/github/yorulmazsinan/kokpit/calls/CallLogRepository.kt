package io.github.yorulmazsinan.kokpit.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recent calls: the head unit's own Bluetooth call history first ([VendorCallLog]), then
 * Android's call log. When neither has anything the home screen hides the row.
 */
class CallLogRepository(private val context: Context, private val scope: CoroutineScope) {
    /** Every known call, newest first; the home screen narrows it with [recentCalls]. */
    private val _calls = MutableStateFlow<List<CallEntry>>(emptyList())
    val calls: StateFlow<List<CallEntry>> = _calls.asStateFlow()
    private var observing = false

    fun refresh() {
        val androidLog = context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!observing) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
            val uris = listOfNotNull(VendorCallLog.uri, CallLog.Calls.CONTENT_URI.takeIf { androidLog })
            observing = uris.map { uri -> runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }.isSuccess }.any { it }
        }
        scope.launch {
            val all = withContext(Dispatchers.IO) { VendorCallLog.query(context) + if (androidLog) query() else emptyList() }
            _calls.value = all.sortedByDescending { it.timeMs }
        }
    }

    /** Pulls the phone's call history onto the unit; the provider observer then refreshes [calls]. */
    fun downloadFromPhone() {
        scope.launch(Dispatchers.IO) { VendorCallLog.download(context) }
    }

    private fun query(): List<CallEntry> = runCatching {
        val projection = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE)
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            buildList {
                while (c.moveToNext() && size < 30) {
                    val type = when (c.getInt(2)) {
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallType.MISSED
                        else -> CallType.INCOMING
                    }
                    add(CallEntry(c.getString(0), c.getString(1).orEmpty(), type, c.getLong(3)))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
