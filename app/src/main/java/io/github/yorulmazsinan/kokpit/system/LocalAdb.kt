package io.github.yorulmazsinan.kokpit.system

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Only this host is ever contacted: the unit's own ADB service, never anything on the network. */
private const val LOOPBACK = "127.0.0.1"
private const val ADB_PORT = 5555

/** A package name, nothing else: letters, digits, `_` and `.` (no spaces, quotes or `;`). */
private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

fun isSafePackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

/**
 * The fixed set of shell commands Kokpit may run. There is deliberately no "run this
 * string" entry: nothing typed, received or produced by the assistant can reach the shell.
 */
object AdbCommands {
    fun grantSecureSettings(ownPackage: String) = "pm grant ${checked(ownPackage)} android.permission.WRITE_SECURE_SETTINGS"
    fun disable(pkg: String) = "pm disable-user --user 0 ${checked(pkg)}"
    fun enable(pkg: String) = "pm enable ${checked(pkg)}"
    fun keepAwake(ownPackage: String) = "dumpsys deviceidle whitelist +${checked(ownPackage)}"
    fun allowAwake(ownPackage: String) = "dumpsys deviceidle whitelist -${checked(ownPackage)}"
    fun allowUsageAccess(ownPackage: String) = "appops set ${checked(ownPackage)} GET_USAGE_STATS allow"
    /** Compiles Kokpit ahead of time now instead of whenever Android feels like it: much faster start on slow units. */
    fun compile(ownPackage: String) = "cmd package compile -m speed -f ${checked(ownPackage)}"
    const val LIST_DISABLED = "pm list packages -d"

    private fun checked(pkg: String): String {
        require(isSafePackageName(pkg)) { "not a package name: $pkg" }
        return pkg
    }
}

/** `package:com.a\npackage:com.b` → {com.a, com.b} */
fun parsePackageList(output: String): Set<String> =
    output.lineSequence().map { it.trim().removePrefix("package:") }.filter(::isSafePackageName).toSet()

data class ElevatedResult(val disabled: List<String>, val failed: List<String>, val permissionGranted: Boolean)

/**
 * "Advanced access": Kokpit talks to the unit's own ADB service over loopback, the way
 * Shizuku/LADB do, to do in one tap what `araclar/hizlandir.sh` does from a computer.
 *
 * Safety rules:
 * - opt-in, started only by the driver from the Speed-up screen; no exported component,
 *   broadcast or assistant tool can start it;
 * - loopback only, and only the commands in [AdbCommands];
 * - Android asks once on screen before the key is trusted; [forget] deletes the key;
 * - it never installs anything (updates always go through Android's installer with a tap);
 * - everything it switches off is recorded and [restore] turns it back on.
 */
class LocalAdb(private val context: Context) {
    private val dir = File(context.filesDir, "adb")
    private val privateKey = File(dir, "adbkey")
    private val publicKey = File(dir, "adbkey.pub")
    private val prefs = context.applicationContext.getSharedPreferences("elevated", Context.MODE_PRIVATE)

    /** Does the unit's ADB service listen on loopback at all? Blocking, quick. */
    fun isAvailable(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(LOOPBACK, ADB_PORT), 600) }
        true
    }.getOrDefault(false)

    val hasKey: Boolean get() = privateKey.exists()

    /** Packages Kokpit switched off through ADB (for [restore]). */
    val disabledByKokpit: Set<String> get() = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()

    /** One-tap speed-up. Blocking: the first time, waits up to a minute for the driver to tap "Allow". */
    fun speedUp(bloat: List<String>): ElevatedResult = session { adb ->
        val own = context.packageName
        val granted = adb.shell(AdbCommands.grantSecureSettings(own)).exitCode == 0
        adb.shell(AdbCommands.allowUsageAccess(own))
        adb.shell(AdbCommands.keepAwake(own))
        adb.shell(AdbCommands.compile(own))
        val alreadyOff = parsePackageList(adb.shell(AdbCommands.LIST_DISABLED).output)
        val done = mutableListOf<String>()
        val failed = mutableListOf<String>()
        bloat.filter(::isSafePackageName).filter { it != own && it !in alreadyOff }.forEach { pkg ->
            val out = adb.shell(AdbCommands.disable(pkg))
            if (out.exitCode == 0 && out.output.contains("disabled")) done += pkg else failed += pkg
        }
        prefs.edit().putStringSet(KEY_DISABLED, disabledByKokpit + done).commit()
        ElevatedResult(done, failed, granted)
    }

    /** Turns back on everything Kokpit switched off and lets Android manage Kokpit's sleep again. */
    fun restore(): List<String> = session { adb ->
        val back = disabledByKokpit.filter { adb.shell(AdbCommands.enable(it)).exitCode == 0 }
        adb.shell(AdbCommands.allowAwake(context.packageName))
        prefs.edit().putStringSet(KEY_DISABLED, disabledByKokpit - back.toSet()).commit()
        back
    }

    /** Deletes Kokpit's ADB key. Revoke it on the unit too: Developer options → Revoke USB debugging authorisations. */
    fun forget() {
        dir.deleteRecursively()
    }

    private fun <T> session(block: (Dadb) -> T): T {
        check(isAvailable()) { "ADB is not listening on this unit" }
        if (!hasKey) {
            dir.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        val keys = AdbKeyPair.read(privateKey, publicKey)
        return Dadb.create(LOOPBACK, ADB_PORT, keys, 3_000, 60_000).use(block)
    }

    private companion object {
        const val KEY_DISABLED = "disabled_by_kokpit"
    }
}
