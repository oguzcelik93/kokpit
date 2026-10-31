package io.github.yorulmazsinan.kokpit.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.yorulmazsinan.kokpit.home.ThemeMode
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import io.github.yorulmazsinan.kokpit.intro.IntroMode
import io.github.yorulmazsinan.kokpit.intro.IntroSettings
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reversible stand-in for the Keystore cipher. */
private object FakeSecretBox : SecretBox {
    override fun encrypt(plain: String): String = Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
    override fun decrypt(encoded: String): String? = runCatching { String(Base64.getDecoder().decode(encoded)).reversed() }.getOrNull()
}

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val file by lazy { File(tmp.root, "settings.preferences_pb") }

    private fun <T> withRepository(secrets: SecretBox = FakeSecretBox, block: suspend (SettingsRepository) -> T): T = runBlocking {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            block(SettingsRepository(store, secrets, scope))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun defaultsMatchSpec() = withRepository { repo ->
        val s = repo.current()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(ThemeAccent.SAAB_93, s.accent)
        assertEquals(true, s.autoWifi)
        assertEquals(false, s.autoCarPlayOnUsb)
        assertEquals(null, s.hotspotSsid)
        assertEquals(IntroSettings(), s.intro)
        assertEquals(IntroMode.SEASONAL, s.intro.mode)
    }

    @Test
    fun updatePersistsAcrossInstances() {
        withRepository { repo ->
            repo.update {
                it.copy(
                    themeMode = ThemeMode.AUTO,
                    accent = ThemeAccent.NIGHT_BLUE,
                    hotspotSsid = "iPhone",
                    hotspotPassword = "gizliparola123",
                    autoCarPlayOnUsb = true,
                    intro = IntroSettings(mode = IntroMode.SEQUENTIAL, lastPlayedIndex = 2),
                )
            }
        }
        val reloaded = withRepository { it.current() }
        assertEquals(ThemeMode.AUTO, reloaded.themeMode)
        assertEquals(ThemeAccent.NIGHT_BLUE, reloaded.accent)
        assertEquals("iPhone", reloaded.hotspotSsid)
        assertEquals("gizliparola123", reloaded.hotspotPassword)
        assertEquals(true, reloaded.autoCarPlayOnUsb)
        assertEquals(IntroMode.SEQUENTIAL, reloaded.intro.mode)
        assertEquals(2, reloaded.intro.lastPlayedIndex)
    }

    @Test
    fun passwordIsNotStoredInPlainText() {
        withRepository { repo -> repo.update { it.copy(hotspotPassword = "gizliparola123") } }
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("gizliparola123"))
    }

    private object BrokenSecretBox : SecretBox {
        override fun encrypt(plain: String): String = throw java.security.KeyStoreException("keymaster gone")
        override fun decrypt(encoded: String): String? = null
    }

    @Test
    fun encryptFailureDoesNotThrow() = withRepository(BrokenSecretBox) { repo ->
        repo.update { it.copy(hotspotSsid = "iPhone", hotspotPassword = "gizliparola123") }
        assertEquals("iPhone", repo.current().hotspotSsid)
    }

    @Test
    fun undecryptablePasswordAsksForReentry() {
        withRepository { repo -> repo.update { it.copy(hotspotPassword = "gizliparola123") } }
        val s = withRepository(BrokenSecretBox) { it.current() }
        assertEquals(null, s.hotspotPassword)
        assertTrue(s.hotspotPasswordUnreadable)
    }
}
