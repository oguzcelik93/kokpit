package io.github.yorulmazsinan.kokpit.car

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens

enum class PlaceKind(val label: Int) { HOME(R.string.place_home), WORK(R.string.place_work) }

fun CarRepository.place(kind: PlaceKind): Place = if (kind == PlaceKind.HOME) home.value else work.value
fun CarRepository.setPlace(kind: PlaceKind, place: Place) = if (kind == PlaceKind.HOME) setHome(place) else setWork(place)

/** Home and work for one-tap navigation: the current GPS spot or a typed address. */
@Composable
fun PlacesScreen(
    car: CarRepository,
    currentPosition: () -> Pair<Double, Double>?,
    onNavigate: (PlaceKind) -> Unit,
    onBack: () -> Unit,
) {
    val home by car.home.collectAsStateWithLifecycle()
    val work by car.work.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.places_title), onBack) {
        LazyColumn {
            item { HelpText(stringResource(R.string.places_help)) }
            item { PlaceEditor(PlaceKind.HOME, home, car, currentPosition, onNavigate) }
            item { PlaceEditor(PlaceKind.WORK, work, car, currentPosition, onNavigate) }
        }
    }
}

@Composable
private fun PlaceEditor(
    kind: PlaceKind,
    place: Place,
    car: CarRepository,
    currentPosition: () -> Pair<Double, Double>?,
    onNavigate: (PlaceKind) -> Unit,
) {
    val context = LocalContext.current
    var address by remember(place) { mutableStateOf(place.address.orEmpty()) }
    SectionHeader(stringResource(kind.label))
    val status = when {
        place.hasPoint -> stringResource(R.string.place_saved_point, "%.5f, %.5f".format(java.util.Locale.US, place.lat, place.lon))
        place.isSet -> stringResource(R.string.place_saved_address, place.address.orEmpty())
        else -> stringResource(R.string.place_not_set)
    }
    HelpText(status)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = {
                val here = currentPosition()
                if (here == null) Toast.makeText(context, R.string.parking_no_fix, Toast.LENGTH_SHORT).show()
                else car.setPlace(kind, Place(here.first, here.second))
            },
            shape = KokpitButtonShape,
            modifier = Modifier.heightIn(min = Dimens.MinTouch),
        ) { Text(stringResource(R.string.place_use_here)) }
        OutlinedButton(
            onClick = { onNavigate(kind) },
            enabled = place.isSet,
            shape = KokpitButtonShape,
            modifier = Modifier.heightIn(min = Dimens.MinTouch),
        ) { Text(stringResource(R.string.place_navigate)) }
        if (place.isSet) {
            TextButton(onClick = { car.setPlace(kind, Place()) }, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                Text(stringResource(R.string.intro_delete))
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            address, { address = it }, Modifier.weight(1f),
            label = { Text(stringResource(R.string.place_address)) },
            singleLine = true,
        )
        Button(
            onClick = { car.setPlace(kind, Place(address = address.trim().ifEmpty { null })) },
            enabled = address.isNotBlank(),
            shape = KokpitButtonShape,
            modifier = Modifier.heightIn(min = Dimens.MinTouch),
        ) { Text(stringResource(R.string.save)) }
    }
}
