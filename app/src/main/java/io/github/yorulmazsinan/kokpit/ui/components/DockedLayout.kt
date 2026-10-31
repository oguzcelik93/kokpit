package io.github.yorulmazsinan.kokpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens

/**
 * A screen with the dock fixed underneath it, so the dock never moves between screens.
 * [banner] (the call in progress) floats just above the dock on every screen, over the
 * content, so no screen has to squeeze its layout while a call lasts.
 */
@Composable
fun DockedLayout(dock: (@Composable () -> Unit)?, banner: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (banner != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding)
                        .shadow(12.dp, RoundedCornerShape(Dimens.CardRadius)),
                ) { banner() }
            }
        }
        if (dock != null) {
            Box(Modifier.padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding)) { dock() }
        }
    }
}
