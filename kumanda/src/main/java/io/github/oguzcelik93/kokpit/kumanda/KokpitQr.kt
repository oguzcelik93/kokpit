package io.github.oguzcelik93.kokpit.kumanda

import java.net.URLDecoder

/**
 * The panel's Bluetooth service id. Kokpit on the unit uses the same id
 * (app/…/panel/PanelRules.kt, PANEL_BT_UUID); change both together.
 */
const val PANEL_BT_UUID = "6b6f6b70-6974-4b75-6d61-6e6461000001"

sealed interface QrResult {
    /** The unit's panel: its one-time [token] and Bluetooth name (null when the unit didn't say). */
    data class Panel(val token: String, val unitName: String?) : QrResult
    /** A Kokpit panel QR, but the unit is not listening on Bluetooth this time. */
    data object NoBluetooth : QrResult
    data object NotKokpit : QrResult
}

/**
 * Reads the QR on the unit's "Telefondan yönet" screen:
 * `http://<ip>:<port>/?t=<token>&bt=<name>` (with Wi-Fi) or `kokpit://panel?t=<token>&bt=<name>`.
 * Only the token and the name are taken; the Wi-Fi address is never used.
 */
fun parsePanelQr(text: String): QrResult {
    val raw = text.trim()
    val query = when {
        raw.startsWith("kokpit://panel?") -> raw.substringAfter('?')
        raw.startsWith("http://") && raw.substringAfter("://").substringBefore('?').endsWith("/") -> raw.substringAfter('?', "")
        else -> return QrResult.NotKokpit
    }
    val params = query.split('&').filter { '=' in it }.associate {
        it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('='), "UTF-8") }.getOrDefault("")
    }
    val token = params["t"]?.takeIf { t -> t.length == 32 && t.all { it in '0'..'9' || it in 'a'..'f' } } ?: return QrResult.NotKokpit
    val name = params["bt"] ?: return QrResult.NoBluetooth
    return QrResult.Panel(token, name.takeIf { it.isNotBlank() && it.none(Char::isISOControl) && it.length <= 248 })
}

/** A paired Bluetooth device, as far as choosing the unit goes. */
data class Paired(val address: String, val name: String?)

/**
 * Which paired devices to try, in order: those named like the unit, then the one that
 * worked last time; only when nothing matches the name, every paired device (a device
 * without the panel's service refuses quickly).
 */
fun tryOrder(paired: List<Paired>, unitName: String?, lastAddress: String?): List<Paired> {
    val named = if (unitName.isNullOrEmpty()) emptyList() else paired.filter { it.name == unitName }
    val last = paired.filter { it.address == lastAddress }
    val rest = if (named.isEmpty()) paired else emptyList()
    return (named + last + rest).distinctBy { it.address }
}
