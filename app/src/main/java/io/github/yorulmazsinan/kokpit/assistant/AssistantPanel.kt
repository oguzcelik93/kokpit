package io.github.yorulmazsinan.kokpit.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens

/** The assistant over whatever screen is open: what it heard, what it says, and a way out. */
@Composable
fun AssistantPanel(
    state: AssistantState,
    providerLabel: String,
    onClose: () -> Unit,
    onTalk: () -> Unit,
    onOpenSettings: () -> Unit,
    onGrantMic: () -> Unit,
) {
    if (state == AssistantState.Hidden) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .testTag("assistant_panel"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.78f)
                // Taps on the card stay on the card.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(providerLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Surface(onClick = onClose, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(Dimens.MinTouch).testTag("assistant_close")) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, stringResource(R.string.assistant_close), tint = MaterialTheme.colorScheme.onSurface) }
                    }
                }
                when (state) {
                    is AssistantState.NeedsSetup -> Setup(state.problem, onOpenSettings, onGrantMic)
                    is AssistantState.Listening -> {
                        Orb(Icons.Outlined.Mic, pulsing = true)
                        Caption(stringResource(if (state.followUp) R.string.assistant_listening_more else R.string.assistant_listening))
                        Heard(state.partial.ifEmpty { " " }, faint = true)
                    }
                    is AssistantState.Thinking -> {
                        Box(Modifier.size(ORB), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(ORB - 8.dp), strokeWidth = 4.dp)
                            Icon(Icons.Outlined.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        }
                        Caption(stringResource(R.string.assistant_thinking))
                        Heard(state.heard)
                    }
                    is AssistantState.Answer -> {
                        if (state.isError) Orb(Icons.Outlined.ErrorOutline, pulsing = false, error = true) else Orb(Icons.Outlined.GraphicEq, pulsing = state.speaking)
                        state.heard?.let { Heard(it, faint = true) }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            state.reply,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()).testTag("assistant_reply"),
                        )
                        state.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        }
                        if (!state.speaking) {
                            Spacer(Modifier.height(18.dp))
                            PanelButton(Icons.Outlined.Mic, stringResource(R.string.assistant_talk_again), onTalk, "assistant_talk")
                        }
                    }
                    AssistantState.Hidden -> Unit
                }
            }
        }
    }
}

private val ORB = 104.dp

@Composable
private fun Orb(icon: ImageVector, pulsing: Boolean, error: Boolean = false) {
    val scale = if (pulsing) {
        val t = rememberInfiniteTransition(label = "orb")
        t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "orb-scale").value
    } else {
        1f
    }
    Box(
        Modifier.padding(vertical = 12.dp).size(ORB).scale(scale).background(
            if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Heard(text: String, faint: Boolean = false) {
    Text(
        "“$text”".takeIf { text.isNotBlank() } ?: " ",
        style = MaterialTheme.typography.titleLarge,
        color = if (faint) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp).testTag("assistant_heard"),
    )
}

@Composable
private fun Setup(problem: SetupProblem, onOpenSettings: () -> Unit, onGrantMic: () -> Unit) {
    Orb(Icons.Outlined.Settings, pulsing = false)
    val (text, button, action) = when (problem) {
        SetupProblem.NO_KEY -> Triple(R.string.assistant_setup_key, R.string.assistant_open_settings, onOpenSettings)
        SetupProblem.NO_MIC_PERMISSION -> Triple(R.string.assistant_setup_mic, R.string.assistant_grant_mic, onGrantMic)
        SetupProblem.NO_RECOGNIZER -> Triple(R.string.assistant_setup_recognizer, R.string.assistant_open_settings, onOpenSettings)
    }
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(18.dp))
    PanelButton(Icons.Outlined.Settings, stringResource(button), action, "assistant_setup_action")
}

@Composable
private fun PanelButton(icon: ImageVector, label: String, onClick: () -> Unit, tag: String) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.heightIn(min = Dimens.MinTouch).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
