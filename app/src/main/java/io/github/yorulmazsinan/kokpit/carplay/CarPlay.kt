package io.github.yorulmazsinan.kokpit.carplay

import android.content.Context
import android.hardware.usb.UsbManager
import io.github.yorulmazsinan.kokpit.dock.parseDockTarget

/** Apple's USB vendor id (same as `res/xml/usb_device_filter.xml`). */
const val APPLE_VENDOR_ID = 0x05AC

enum class EngineKind {
    /** CarPlay stack baked into the head unit's firmware: no extra app, fastest start. */
    NATIVE,

    /** ZLink: a separate app using the unit's Apple authentication chip. */
    ZLINK,

    /** AutoKit: talks to a Carlinkit USB adapter that does CarPlay itself. */
    ADAPTER,

    /** Phone mirroring; Android Auto-like, last resort. */
    MIRROR,
}

/**
 * Apps that can bring up CarPlay, in the order Kokpit prefers them. CarPlay needs Apple's
 * authentication chip (MFi), so no app can speak it on its own; Kokpit's "direct" mode
 * skips ZLink by using the firmware's own CarPlay when the unit has one, and skips
 * splash screens by launching the engine's working screen directly.
 */
data class CarPlayEngine(val target: String, val kind: EngineKind, val name: String) {
    val packageName: String get() = parseDockTarget(target).packageName
}

val CARPLAY_ENGINES: List<CarPlayEngine> = listOf(
    CarPlayEngine("com.ts.carplayapp/com.autochips.carplayapp.LoadingActivity", EngineKind.NATIVE, "PhoneLink (cihazın kendi CarPlay'i)"),
    CarPlayEngine("com.zjinnova.zlink", EngineKind.ZLINK, "ZLink"),
    CarPlayEngine("cn.manstep.phonemirrorBox", EngineKind.ADAPTER, "AutoKit (Carlinkit adaptörü)"),
    CarPlayEngine("net.easyconn", EngineKind.MIRROR, "EasyConnection"),
)

/** Engines whose package is installed, best first. */
fun installedEngines(installed: Set<String>): List<CarPlayEngine> = CARPLAY_ENGINES.filter { it.packageName in installed }

/** The engine Kokpit uses when the user picked nothing: native firmware CarPlay wins over ZLink. */
fun preferredEngine(installed: Set<String>): CarPlayEngine? = installedEngines(installed).firstOrNull()

fun engineFor(target: String?, installed: Set<String>): CarPlayEngine? {
    val pkg = target?.let { parseDockTarget(it).packageName } ?: return preferredEngine(installed)
    return CARPLAY_ENGINES.firstOrNull { it.packageName == pkg }
}

/** Is an iPhone (or any Apple device) on the USB port right now? */
fun isIphoneAttached(context: Context): Boolean = runCatching {
    context.getSystemService(UsbManager::class.java).deviceList.values.any { it.vendorId == APPLE_VENDOR_ID }
}.getOrDefault(false)
