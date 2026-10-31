package io.github.yorulmazsinan.kokpit.dock

import androidx.annotation.StringRes
import io.github.yorulmazsinan.kokpit.R

enum class DockSlot(@StringRes val label: Int, val inDock: Boolean = true) {
    CARPLAY(R.string.dock_carplay),
    BLUETOOTH(R.string.dock_bluetooth),
    PHONE(R.string.dock_phone),
    RADIO(R.string.dock_radio),
    MAPS(R.string.dock_maps),
    APPS(R.string.dock_apps),

    /** The home screen's microphone button; phase 1 can point it at an app such as ChatGPT. */
    ASSISTANT(R.string.dock_assistant, inDock = false),
}
