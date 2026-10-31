package io.github.yorulmazsinan.kokpit.dock

/**
 * What a dock button opens: a whole app, or one screen of an app. Head units bundle Radio,
 * Bluetooth and Phone as separate screens of a single vendor package (e.g. com.ts.MainUI).
 */
sealed interface DockTarget {
    val packageName: String

    data class Package(override val packageName: String) : DockTarget
    data class Component(override val packageName: String, val className: String) : DockTarget
}

/** Parses a stored target: `package` or `package/class` (`ComponentName.flattenToString`). */
fun parseDockTarget(value: String): DockTarget {
    val slash = value.indexOf('/')
    if (slash < 0) return DockTarget.Package(value)
    val pkg = value.substring(0, slash)
    val cls = value.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
    return DockTarget.Component(pkg, cls)
}

/** Target to launch for [slot]: the user's choice if its app is still installed, else the first known candidate. */
fun resolveDockTarget(slot: DockSlot, installed: Set<String>, override: String?): String? {
    if (slot == DockSlot.APPS) return null
    if (override != null && parseDockTarget(override).packageName in installed) return override
    return KnownPackages.candidates[slot].orEmpty().firstOrNull { parseDockTarget(it).packageName in installed }
}
