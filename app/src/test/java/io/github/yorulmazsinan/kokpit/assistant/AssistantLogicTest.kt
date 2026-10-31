package io.github.yorulmazsinan.kokpit.assistant

import android.speech.SpeechRecognizer
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.phone.Contact
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantLogicTest {
    private class Fake : AssistantActions {
        val done = mutableListOf<String>()
        override fun openApp(name: String) = "open $name".also(done::add)
        override fun media(action: String) = "media $action".also(done::add)
        override fun navigate(destination: String) = "nav $destination".also(done::add)
        override fun tuneRadio(tenths: Int) = "radio $tenths".also(done::add)
        override fun call(query: String) = "call $query".also(done::add)
        override fun volume(action: String) = "volume $action".also(done::add)
        override fun status() = "status".also(done::add)
        override fun connectPhoneWifi() = "wifi".also(done::add)
    }

    private fun args(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) -> k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) })

    @Test
    fun toolsReachTheDevice() {
        val fake = Fake()
        assertEquals("radio 984", runTool("radio_tune", args("frequency_mhz" to 98.4), fake))
        assertEquals("radio 1040", runTool("radio_tune", args("frequency_mhz" to "104,0"), fake))
        assertTrue(runTool("radio_tune", args("frequency_mhz" to 120), fake).startsWith("Error:"))
        assertEquals("call Annem", runTool("call_contact", args("query" to " Annem "), fake))
        assertEquals("status", runTool("car_status", JsonObject(emptyMap()), fake))
        assertTrue(runTool("launch_rocket", JsonObject(emptyMap()), fake).startsWith("Error:"))
        assertEquals(listOf("radio 984", "radio 1040", "call Annem", "status"), fake.done)
    }

    @Test
    fun everyToolSchemaIsStrictReady() {
        ASSISTANT_TOOLS.forEach { tool ->
            assertEquals(tool.name, JsonPrimitive(false), tool.parameters["additionalProperties"])
            assertEquals(tool.name, "object", (tool.parameters["type"] as JsonPrimitive).content)
        }
        assertEquals(ASSISTANT_TOOLS.size, ASSISTANT_TOOLS.map { it.name }.toSet().size)
    }

    @Test
    fun contactsMatchTheWayPeopleSayThem() {
        val book = listOf(
            Contact("Annem", "0533", true),
            Contact("Ayşe Yılmaz", "0532", false),
            Contact("Ayşe Kaya", "0542", false),
            Contact("Ali Yılmaz", "0555", false),
        )
        assertEquals(listOf("Annem"), matchContacts("annem", book).map { it.name })
        assertEquals(listOf("Ayşe Yılmaz"), matchContacts("ayse yilmaz", book).map { it.name })
        assertEquals(setOf("Ayşe Yılmaz", "Ayşe Kaya"), matchContacts("Ayşe", book).map { it.name }.toSet())
        assertEquals(listOf("Ali Yılmaz"), matchContacts("ali yıl", book).map { it.name })
        assertTrue(matchContacts("Mehmet", book).isEmpty())
        assertEquals("05321112233", spokenNumber("0532 111 22 33"))
        assertNull(spokenNumber("Annem"))
    }

    @Test
    fun screensByTheirSpokenNames() {
        assertEquals(DockSlot.RADIO, screenFor("Radyoyu"))
        assertEquals(DockSlot.CARPLAY, screenFor("CarPlay"))
        assertEquals(DockSlot.BLUETOOTH, screenFor("müzik"))
        assertEquals("player", screenFor("USB"))
        assertEquals("quick", screenFor("hızlı ayarlar"))
        assertEquals("settings", screenFor("ayarlar"))
        assertNull(screenFor("YouTube"))
    }

    @Test
    fun conversationEndsOnAThankYou() {
        assertTrue(isGoodbye("Teşekkürler"))
        assertTrue(isGoodbye("tamam"))
        assertEquals(false, isGoodbye("tamam, radyoyu aç"))
    }

    @Test
    fun recogniserErrorsBecomeActions() {
        assertEquals(Heard.Nothing, heardForError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(Heard.NoPermission, heardForError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertTrue((heardForError(SpeechRecognizer.ERROR_NETWORK) as Heard.Failed).message.contains("internet"))
    }

    @Test
    fun settingsPickTheModelAndKeyOfTheChosenProvider() {
        val s = AssistantSettings(provider = AiProvider.GEMINI, keys = mapOf(AiProvider.CLAUDE to "a", AiProvider.GEMINI to " "))
        assertNull(s.key)
        assertEquals("gemini-3.8-flash", s.model)
        assertEquals("claude-opus-5-5", s.copy(provider = AiProvider.CLAUDE).model)
        assertEquals("a", s.copy(provider = AiProvider.CLAUDE).key)
    }
}
