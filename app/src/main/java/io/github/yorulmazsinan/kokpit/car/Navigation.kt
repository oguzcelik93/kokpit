package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.dock.parseDockTarget

/**
 * Starts navigation to home or work in the maps app on the Maps button (Google Maps, Waze,
 * Yandex), falling back to any app that understands `geo:`. False when the place is unset.
 */
fun navigateTo(context: Context, container: AppContainer, kind: PlaceKind, mapsTarget: String?): Boolean {
    val place = container.car.place(kind)
    val label = context.getString(kind.label)
    val pkg = mapsTarget?.let { parseDockTarget(it).packageName }
    val spec = navigationSpec(place, pkg, label) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spec.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val withPackage = spec.packageName?.let { Intent(intent).setPackage(it) }
    val started = listOfNotNull(withPackage, intent, navigationSpec(place, null, label)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        .any { runCatching { context.startActivity(it) }.isSuccess }
    if (!started) Toast.makeText(context, R.string.start_failed, Toast.LENGTH_SHORT).show()
    return true
}

/** Long-press on Home / Work: the car's current spot becomes that place. */
fun saveHere(context: Context, container: AppContainer, kind: PlaceKind) {
    val here = container.drive.lastPosition()
    if (here == null) {
        Toast.makeText(context, R.string.parking_no_fix, Toast.LENGTH_SHORT).show()
        return
    }
    container.car.setPlace(kind, Place(here.first, here.second))
    Toast.makeText(context, context.getString(R.string.place_saved_toast, context.getString(kind.label)), Toast.LENGTH_SHORT).show()
}

