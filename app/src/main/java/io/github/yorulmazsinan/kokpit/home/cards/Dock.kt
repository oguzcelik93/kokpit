package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.dock.DockSlot

@Composable
fun Dock(
    onClick: (DockSlot) -> Unit,
    onLongClick: (DockSlot) -> Unit,
    modifier: Modifier = Modifier,
    active: DockSlot? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DockSlot.entries.filter { it.inDock }.forEach { slot ->
            DockButton(slot, slot == active, { onClick(slot) }, { onLongClick(slot) }, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockButton(slot: DockSlot, highlighted: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier
            .heightIn(min = 76.dp)
            .testTag("dock_${slot.name}")
            .semantics { selected = highlighted },
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (highlighted) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                slot.icon(),
                null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(slot.label),
                style = MaterialTheme.typography.titleSmall,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

fun DockSlot.icon(): ImageVector = when (this) {
    DockSlot.CARPLAY -> Icons.Outlined.DirectionsCar
    DockSlot.BLUETOOTH -> Icons.Outlined.MusicNote
    DockSlot.PHONE -> Icons.Outlined.Phone
    DockSlot.RADIO -> Icons.Outlined.Radio
    DockSlot.MAPS -> Icons.Outlined.Map
    DockSlot.APPS -> Icons.Outlined.Apps
    DockSlot.ASSISTANT -> Icons.Outlined.Mic
}
