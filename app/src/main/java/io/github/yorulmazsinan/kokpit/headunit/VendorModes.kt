package io.github.yorulmazsinan.kokpit.headunit

/** Audio sources with the work mode the vendor screen sets in its `onResume` (`Evc.evol_workmode_set`). */
enum class VendorSource(val workMode: Int) { RADIO(1), BT_MUSIC(5) }

/**
 * Switches the head unit's audio source the way the vendor screen does, through
 * `ITsRadioCommon.Evc_evol_workmode_set`, so the vendor screen never has to open.
 * These are blocking binder calls.
 */
class VendorModes(private val headUnit: HeadUnit) {
    fun isActive(source: VendorSource): Boolean = headUnit.common?.workMode() == source.workMode

    /** False when the unit can't be switched this way; the caller then opens the vendor screen. */
    fun enter(source: VendorSource): Boolean {
        if (isActive(source)) return true
        if (headUnit.radioCommon()?.setWorkMode(source.workMode) != true) return false
        repeat(10) {
            if (isActive(source)) return true
            Thread.sleep(100)
        }
        return isActive(source)
    }
}
