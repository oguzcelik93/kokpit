package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.theme.LocalCockpitStyle

/** What the window card shows: the driver's photo and the app it opens (Brave, by default). */
data class WindowCardState(
    val image: ImageBitmap?,
    val appLabel: String?,
    val appIcon: ImageBitmap?,
    /** Blurred fill behind a portrait photo. */
    val backdrop: ImageBitmap? = null,
    val portrait: Boolean = false,
)

/**
 * The home screen's window: a photo of the driver's choice, with a one-tap button for the
 * browser (or any app). Android does not let one app show another app inside it, so the
 * browser opens full screen — which is also how it runs fastest and most stably.
 */
@Composable
fun WindowCard(state: WindowCardState?, onOpen: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(LocalCockpitStyle.current.cardRadius)
    val hasApp = state?.appLabel != null
    Surface(
        modifier.testTag("window_card").combinedClickable(onClick = if (hasApp) onOpen else onSettings, onLongClick = onSettings),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            val image = state?.image
            if (image != null) {
                if (state.portrait) {
                    // A portrait photo fits whole; its own blurred, darkened copy fills the sides.
                    Image(state.backdrop ?: image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                    Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    // A landscape photo fills the card, uncovered; a tap still opens the app.
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (hasApp && image == null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(14.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = state?.appIcon
                    if (icon != null) Image(icon, null, Modifier.size(30.dp)) else Icon(Icons.Outlined.Language, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(state?.appLabel.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            } else if (image == null) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.window_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
