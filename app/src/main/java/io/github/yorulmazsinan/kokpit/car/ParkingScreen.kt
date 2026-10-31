package io.github.yorulmazsinan.kokpit.car

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.home.cards.KokpitCard
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens

/**
 * "Where did I leave the car?": scan the QR code with the phone before walking away. The
 * spot is also saved by itself when the ignition goes off.
 */
@Composable
fun ParkingScreen(
    car: CarRepository,
    currentPosition: () -> Pair<Double, Double>?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val parked by car.parked.collectAsStateWithLifecycle()
    val here = currentPosition()
    ScreenScaffold(stringResource(R.string.parking_title), onBack) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            SpotCard(
                title = stringResource(R.string.parking_here),
                subtitle = stringResource(R.string.parking_scan),
                point = here,
                empty = stringResource(R.string.parking_no_fix),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Button(
                    onClick = { here?.let { (lat, lon) -> car.park(lat, lon) } },
                    enabled = here != null,
                    shape = KokpitButtonShape,
                    modifier = Modifier.heightIn(min = Dimens.MinTouch),
                ) { Text(stringResource(R.string.parking_save_here)) }
            }
            SpotCard(
                title = stringResource(R.string.parking_last),
                subtitle = parked?.let {
                    DateUtils.getRelativeDateTimeString(context, it.timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
                } ?: "",
                point = parked?.let { it.lat to it.lon },
                empty = stringResource(R.string.parking_none),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                OutlinedButton(
                    onClick = { parked?.let { safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${it.lat},${it.lon}?q=${it.lat},${it.lon}"))) } },
                    enabled = parked != null,
                    shape = KokpitButtonShape,
                    modifier = Modifier.heightIn(min = Dimens.MinTouch),
                ) { Text(stringResource(R.string.parking_open_map)) }
            }
        }
    }
}

@Composable
private fun SpotCard(
    title: String,
    subtitle: String,
    point: Pair<Double, Double>?,
    empty: String,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    KokpitCard(modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            if (point != null) {
                QrCode(mapsLink(point.first, point.second), Modifier.weight(1f))
                Text(
                    "%.5f, %.5f".format(java.util.Locale.US, point.first, point.second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(empty, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(top = 24.dp))
            }
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}
