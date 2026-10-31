package io.github.yorulmazsinan.kokpit.intro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import java.io.File
import java.util.Locale

/** Videos found on USB sticks / SD cards; all are pre-selected. */
@Composable
fun UsbVideoDialog(found: List<File>, onDismiss: () -> Unit, onImport: (List<File>) -> Unit) {
    val selected = remember(found) { mutableStateListOf<File>().apply { addAll(found) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.intro_usb_title)) },
        text = {
            if (found.isEmpty()) {
                Text(stringResource(R.string.intro_usb_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(found, key = { it.path }) { file ->
                        Row(
                            Modifier
                                .heightIn(min = Dimens.MinTouch)
                                .clickable { if (file in selected) selected.remove(file) else selected.add(file) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = file in selected, onCheckedChange = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    String.format(Locale.getDefault(), "%.1f MB · %s", file.length() / 1_048_576.0, file.parent.orEmpty()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (found.isNotEmpty()) {
                TextButton(onClick = { onImport(selected.toList()) }, enabled = selected.isNotEmpty()) {
                    Text(stringResource(R.string.intro_usb_import, selected.size))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
