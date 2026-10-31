package io.github.yorulmazsinan.kokpit.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import io.github.yorulmazsinan.kokpit.BuildConfig
import io.github.yorulmazsinan.kokpit.connect.WakeEvent
import io.github.yorulmazsinan.kokpit.connect.WakeEvents
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Where Kokpit looks for new versions: the latest GitHub release of the public `kokpit`
 * repository (built and signed by its release workflow). HTTPS only; the APK's SHA-256 is
 * checked and Android refuses any APK not signed with the installed app's key.
 */
const val UPDATE_BASE_URL = "https://github.com/oguzcelik93/kokpit/releases/latest/download/"

/** `version.json` on the site. */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val sizeBytes: Long = 0,
    val notes: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMs: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/** `kokpit.apk`, `Kokpit-0.7.1.apk` … on a stick's root or its `Kokpit/` folder. */
fun usbApkCandidates(roots: List<File>): List<File> =
    roots.flatMap { root -> listOf(root, File(root, "Kokpit")) }
        .flatMap { dir -> dir.listFiles().orEmpty().toList() }
        .filter { it.isFile && it.name.startsWith("kokpit", ignoreCase = true) && it.name.endsWith(".apk", ignoreCase = true) }
        .distinctBy { it.absolutePath }

fun isNewer(info: UpdateInfo, currentCode: Int): Boolean = info.versionCode > currentCode

/** `apk` may be relative to `version.json` ("kokpit-0.7.1.apk") or absolute. */
fun resolveApkUrl(base: String, apk: String): String = base.toHttpUrl().resolve(apk)?.toString() ?: apk

private val json = Json { ignoreUnknownKeys = true }

fun parseUpdateInfo(text: String): UpdateInfo = json.decodeFromString(UpdateInfo.serializer(), text)

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Over-the-air updates from the website. Kokpit checks `version.json`, downloads the APK,
 * checks its SHA-256, and hands it to Android's installer — the driver confirms with one
 * tap (Android allows silent installs only for system apps). Android itself refuses an
 * APK not signed with the same key as the installed Kokpit.
 */
class Updater(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val baseUrl: String = UPDATE_BASE_URL,
    private val currentCode: Int = BuildConfig.VERSION_CODE,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val prefs = context.applicationContext.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val dir = File(context.cacheDir, "updates")

    /** Checks on start and on every ignition-on, at most every six hours. */
    fun startAuto(enabled: () -> Boolean) {
        fun maybe() {
            if (!enabled()) return
            if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < AUTO_INTERVAL_MS) return
            scope.launch { check(downloadIfNew = true) }
        }
        maybe()
        scope.launch { WakeEvents.flow.collect { if (it is WakeEvent.Wake) maybe() } }
    }

    suspend fun check(downloadIfNew: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("${baseUrl}version.json?t=${System.currentTimeMillis()}").header("Cache-Control", "no-cache").build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    parseUpdateInfo(response.body.string())
                }
            }
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        val info = result.getOrElse {
            _state.value = UpdateState.Failed(it.message ?: it.javaClass.simpleName)
            return
        }
        if (!isNewer(info, currentCode)) {
            cleanup()
            _state.value = UpdateState.UpToDate(System.currentTimeMillis())
            return
        }
        // Already downloaded earlier and still intact.
        val existing = apkFile(info)
        if (existing.exists() && withContext(Dispatchers.IO) { sha256Hex(existing) }.equals(info.sha256, ignoreCase = true)) {
            _state.value = UpdateState.Ready(info, existing)
            return
        }
        _state.value = UpdateState.Available(info)
        if (downloadIfNew) download(info)
    }

    suspend fun download(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0)
        val target = apkFile(info)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                dir.listFiles().orEmpty().filter { it != target }.forEach { it.delete() }
                val partial = File(dir, target.name + ".part")
                val request = Request.Builder().url(resolveApkUrl(baseUrl, info.apk)).build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                    var done = 0L
                    var lastPercent = -1
                    partial.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                done += n
                                val percent = if (total > 0) (done * 100 / total).toInt() else 0
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _state.value = UpdateState.Downloading(info, percent)
                                }
                            }
                        }
                    }
                }
                val actual = sha256Hex(partial)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    partial.delete()
                    error("SHA-256 tutmuyor")
                }
                if (target.exists()) target.delete()
                check(partial.renameTo(target)) { "dosya taşınamadı" }
                target
            }
        }
        _state.value = result.fold({ UpdateState.Ready(info, it) }, { UpdateState.Failed(it.message ?: it.javaClass.simpleName) })
    }

    /**
     * USB stick: a newer Kokpit APK (`kokpit*.apk`, on the root or in `Kokpit/`) is copied to
     * the unit, so the stick can be pulled out right away, and offered for install. Blocking.
     */
    fun checkUsb(roots: List<File>): UpdateInfo? {
        val candidates = usbApkCandidates(roots)
        val best = candidates.mapNotNull { file ->
            @Suppress("DEPRECATION")
            val info = runCatching { context.packageManager.getPackageArchiveInfo(file.path, 0) }.getOrNull() ?: return@mapNotNull null
            if (info.packageName != context.packageName) return@mapNotNull null
            Triple(file, PackageInfoCompat.getLongVersionCode(info).toInt(), info.versionName.orEmpty())
        }.maxByOrNull { it.second } ?: return null
        val (file, code, name) = best
        if (code <= currentCode) return null
        val current = _state.value
        if (current is UpdateState.Ready && current.info.versionCode >= code) return current.info
        dir.mkdirs()
        dir.listFiles().orEmpty().forEach { it.delete() }
        val target = File(dir, "kokpit-$code.apk")
        file.copyTo(target, overwrite = true)
        val info = UpdateInfo(code, name, file.name, sha256Hex(target), target.length(), notes = "USB bellekten: ${file.name}")
        _state.value = UpdateState.Ready(info, target)
        return info
    }

    /** Can Kokpit open Android's installer? On 8.0+ the user allows it once per app. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun allowInstallIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        @Suppress("DEPRECATION")
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
    }

    private fun apkFile(info: UpdateInfo) = File(dir, "kokpit-${info.versionCode}.apk")

    private fun cleanup() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val AUTO_INTERVAL_MS = 6 * 3_600_000L
    }
}
