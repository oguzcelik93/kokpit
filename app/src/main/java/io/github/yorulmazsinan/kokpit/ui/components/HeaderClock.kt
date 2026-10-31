package io.github.yorulmazsinan.kokpit.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Small accent-coloured clock for the right side of screen headers. */
@Composable
fun HeaderClock() {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
