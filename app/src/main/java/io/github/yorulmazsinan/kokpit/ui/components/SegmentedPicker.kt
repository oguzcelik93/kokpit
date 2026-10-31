package io.github.yorulmazsinan.kokpit.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PickerHeight = 48.dp
private val PickerInset = 4.dp
private val TrackRadius = 12.dp
private val ThumbRadius = 9.dp

/**
 * The one choice control in Kokpit, after Apple's segmented picker: a single track with a
 * thumb that slides to the chosen segment. Every screen uses it at the same height,
 * type size and padding.
 */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(PickerHeight)
            .clip(RoundedCornerShape(TrackRadius))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(TrackRadius))
            .padding(PickerInset),
    ) {
        val segment = maxWidth / options.size
        val thumbX by animateDpAsState(segment * index, tween(220), label = "picker_thumb")
        Box(
            Modifier
                .offset(x = thumbX)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(ThumbRadius))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, option ->
                val chosen = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(ThumbRadius))
                        .selectable(selected = chosen, role = Role.Tab) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        fontSize = 16.sp,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

/** [SegmentedPicker] placed as a row of a settings list, with the list's own margins. */
@Composable
fun <T> SettingsPicker(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    SegmentedPicker(
        options,
        selected,
        onSelect,
        label,
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 640.dp),
    )
}

/** Buttons share the picker's corners instead of Material's full pill. */
val KokpitButtonShape = RoundedCornerShape(TrackRadius)
