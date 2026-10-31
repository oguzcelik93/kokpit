package io.github.yorulmazsinan.kokpit.calls

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.home.cards.elapsedText
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.delay

private val Decline = Color(0xFFD9534F)

/** The call in progress, above the dock on every screen: answer, decline or hang up in one tap. */
@Composable
fun CallBar(call: ActiveCall, onAnswer: () -> Unit, onHangUp: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val now by produceState(SystemClock.elapsedRealtime(), call.connectedAt) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val status = when (call.phase) {
        CallPhase.INCOMING -> stringResource(R.string.call_incoming)
        CallPhase.DIALING -> stringResource(R.string.call_dialing)
        CallPhase.HELD -> stringResource(R.string.call_held)
        CallPhase.ACTIVE -> call.connectedAt?.let { elapsedText(now - it) } ?: stringResource(R.string.call_active)
    }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag("call_bar"),
        shape = RoundedCornerShape(Dimens.CardRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.CardPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Call, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    call.title.ifEmpty { stringResource(R.string.call_unknown) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            if (call.phase == CallPhase.INCOMING) {
                CallButton(Icons.Outlined.CallEnd, stringResource(R.string.call_decline), Decline, onHangUp, "call_decline")
                CallButton(Icons.Outlined.Call, stringResource(R.string.call_answer), MaterialTheme.colorScheme.primary, onAnswer, "call_answer")
            } else {
                CallButton(Icons.Outlined.CallEnd, stringResource(R.string.phone_hang_up), Decline, onHangUp, "call_hang_up")
            }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit, tag: String) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = color,
        modifier = Modifier.heightIn(min = Dimens.MinTouch).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp).align(Alignment.CenterVertically))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(vertical = 18.dp))
        }
    }
}
