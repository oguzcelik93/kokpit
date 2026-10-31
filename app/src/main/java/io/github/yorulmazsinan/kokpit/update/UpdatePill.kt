package io.github.yorulmazsinan.kokpit.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R

/** Small "update ready" note at the top of the home screen; tap for the update page. */
@Composable
fun UpdatePill(state: UpdateState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = when (state) {
        is UpdateState.Ready -> state.info.versionName
        is UpdateState.Available -> state.info.versionName
        else -> return
    }
    InfoPill(stringResource(R.string.update_pill, name), modifier.testTag("update_pill"), onClick)
}

/** A small rounded note at the top of the home screen. */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.padding(top = 6.dp).clickable(onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
