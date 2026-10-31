package io.github.yorulmazsinan.kokpit.update

import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    private val server = MockWebServer()
    private val apk = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun updater(current: Int) = Updater(
        ApplicationProvider.getApplicationContext(), OkHttpClient(), CoroutineScope(Dispatchers.Unconfined),
        baseUrl = server.url("/kokpit/").toString(), currentCode = current,
    )

    private fun versionJson(code: Int, hash: String = sha) =
        """{"versionCode":$code,"versionName":"0.7.$code","apk":"kokpit-0.7.$code.apk","sha256":"$hash","sizeBytes":${apk.size},"notes":"Yeni","extra":1}"""

    @Test
    fun downloadsVerifiesAndBecomesReady() = runBlocking {
        server.enqueue(MockResponse.Builder().body(versionJson(18)).build())
        server.enqueue(MockResponse.Builder().body(Buffer().write(apk)).build())
        val u = updater(17)
        u.check(downloadIfNew = true)
        val state = u.state.value
        assertTrue(state.toString(), state is UpdateState.Ready)
        val file = (state as UpdateState.Ready).file
        assertEquals(sha, sha256Hex(file))
        assertTrue(server.takeRequest().target.startsWith("/kokpit/version.json"))
        assertEquals("/kokpit/kokpit-0.7.18.apk", server.takeRequest().target)
    }

    @Test
    fun wrongHashIsRejected() = runBlocking {
        server.enqueue(MockResponse.Builder().body(versionJson(18, "00".repeat(32))).build())
        server.enqueue(MockResponse.Builder().body(Buffer().write(apk)).build())
        val u = updater(17)
        u.check(downloadIfNew = true)
        assertTrue(u.state.value is UpdateState.Failed)
        val leftovers = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "updates").listFiles().orEmpty()
        assertFalse(leftovers.any { it.name.endsWith(".apk") })
    }

    @Test
    fun sameVersionIsUpToDate() = runBlocking {
        server.enqueue(MockResponse.Builder().body(versionJson(17)).build())
        val u = updater(17)
        u.check(downloadIfNew = true)
        assertTrue(u.state.value is UpdateState.UpToDate)
    }

    @Test
    fun serverErrorFails() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        val u = updater(17)
        u.check()
        assertTrue(u.state.value is UpdateState.Failed)
    }

    @Test
    fun findsKokpitApksOnTheStick() {
        val root = kotlin.io.path.createTempDirectory("usb").toFile()
        File(root, "Kokpit-0.7.1.apk").writeText("x")
        File(root, "Kokpit").mkdirs(); File(root, "Kokpit/kokpit.apk").writeText("x")
        File(root, "baska.apk").writeText("x")
        File(root, "kokpit-notlar.txt").writeText("x")
        assertEquals(setOf("Kokpit-0.7.1.apk", "kokpit.apk"), usbApkCandidates(listOf(root)).map { it.name }.toSet())
    }

    @Test
    fun usbIgnoresApksThatAreNotKokpit() {
        val root = kotlin.io.path.createTempDirectory("usb").toFile()
        File(root, "kokpit.apk").writeBytes(apk) // not a real APK: no package info
        assertEquals(null, updater(17).checkUsb(listOf(root)))
    }

    @Test
    fun urls() {
        assertEquals("https://example.org/kokpit/kokpit-0.7.1.apk", resolveApkUrl("https://example.org/kokpit/", "kokpit-0.7.1.apk"))
        assertEquals("https://cdn.example/x.apk", resolveApkUrl("https://example.org/kokpit/", "https://cdn.example/x.apk"))
        assertTrue(isNewer(UpdateInfo(18, "", "", ""), 17))
        assertFalse(isNewer(UpdateInfo(17, "", "", ""), 17))
    }
}
