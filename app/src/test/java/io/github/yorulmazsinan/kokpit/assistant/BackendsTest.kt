package io.github.yorulmazsinan.kokpit.assistant

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class BackendsTest {
    private val server = MockWebServer()
    private val calls = mutableListOf<Pair<String, JsonObject>>()
    private val runner: ToolRunner = { name, args ->
        calls += name to args
        "Radio tuned to 98.4 FM."
    }

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private val base get() = server.url("/").toString().trimEnd('/')
    private fun reply(json: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(json).build())
    private fun RecordedRequest.json(): JsonObject = aiJson.parseToJsonElement(body!!.utf8()).jsonObject
    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    @Test
    fun claudeRunsToolsKeepsThinkingAndSpeaksTheAnswer() = runTest {
        reply("""{"stop_reason":"tool_use","content":[{"type":"thinking","thinking":"","signature":"sig1"},{"type":"tool_use","id":"tu1","name":"radio_tune","input":{"frequency_mhz":98.4}}]}""")
        reply("""{"stop_reason":"end_turn","content":[{"type":"text","text":"Radyoyu 98 nokta 4'e "},{"type":"text","text":"ayarladım."}]}""")
        val answer = ClaudeBackend("sk-ant-x", "claude-opus-5-5", ASSISTANT_TOOLS, base).reply("98.4'ü aç", runner)

        assertEquals("Radyoyu 98 nokta 4'e ayarladım.", answer)
        assertEquals("radio_tune", calls.single().first)
        val first = server.takeRequest()
        assertEquals("/v1/messages", first.url.encodedPath)
        assertEquals("sk-ant-x", first.headers["x-api-key"])
        assertEquals("2026-10-21", first.headers["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", first.headers["anthropic-beta"])
        val body = first.json()
        assertEquals("claude-opus-5-5", body.str("model"))
        assertEquals("default", body.str("fallbacks"))
        assertEquals("adaptive", body["thinking"]!!.jsonObject.str("type"))
        assertEquals("low", body["output_config"]!!.jsonObject.str("effort"))
        val tools = body["tools"]!!.jsonArray.map { it.jsonObject }
        assertTrue(tools.any { it["type"]?.jsonPrimitive?.content == "web_search_20260209" })
        assertTrue(tools.filter { it["name"]?.jsonPrimitive?.content == "radio_tune" }.single()["strict"]!!.jsonPrimitive.content == "true")

        val messages = server.takeRequest().json()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.str("role") })
        // The thinking block goes back exactly as it came.
        assertEquals("sig1", messages[1]["content"]!!.jsonArray[0].jsonObject.str("signature"))
        val result = messages[2]["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", result.str("type"))
        assertEquals("tu1", result.str("tool_use_id"))
    }

    @Test
    fun claudeResumesAPausedTurnWithoutAnExtraUserMessage() = runTest {
        reply("""{"stop_reason":"pause_turn","content":[{"type":"server_tool_use","id":"s1","name":"web_search","input":{"query":"maç"}}]}""")
        reply("""{"stop_reason":"end_turn","content":[{"type":"text","text":"Maç 2-1 bitti."}]}""")
        assertEquals("Maç 2-1 bitti.", ClaudeBackend("k", "m", ASSISTANT_TOOLS, base).reply("maç kaç bitti", runner))
        server.takeRequest()
        val messages = server.takeRequest().json()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant"), messages.map { it.str("role") })
    }

    @Test
    fun claudeRefusalLeavesNoHalfTurn() = runTest {
        reply("""{"stop_reason":"refusal","content":[]}""")
        reply("""{"stop_reason":"end_turn","content":[{"type":"text","text":"Tamam."}]}""")
        val backend = ClaudeBackend("k", "m", ASSISTANT_TOOLS, base)
        assertEquals("Bu isteğe yardımcı olamıyorum.", backend.reply("x", runner))
        backend.reply("merhaba", runner)
        server.takeRequest()
        val messages = server.takeRequest().json()["messages"]!!.jsonArray
        assertEquals(1, messages.size)
    }

    @Test
    fun badKeyIsSpokenPlainly() = runTest {
        reply("""{"type":"error","error":{"type":"authentication_error"}}""", code = 401)
        try {
            ClaudeBackend("bad", "m", ASSISTANT_TOOLS, base).reply("x", runner)
            fail()
        } catch (e: AiException) {
            assertTrue(e.spoken.contains("API anahtarı"))
            assertTrue(e.detail!!.startsWith("HTTP 401"))
        }
    }

    @Test
    fun openAiChainsResponsesAndReturnsFunctionOutputs() = runTest {
        reply("""{"id":"r1","output":[{"type":"function_call","call_id":"c1","name":"volume","arguments":"{\"action\":\"up\"}"}]}""")
        reply("""{"id":"r2","output":[{"type":"message","content":[{"type":"output_text","text":"Sesi açtım."}]}]}""")
        reply("""{"id":"r3","output":[{"type":"message","content":[{"type":"output_text","text":"Rica ederim."}]}]}""")
        val backend = OpenAiBackend("sk-x", "gpt-6-astra", ASSISTANT_TOOLS, base)
        assertEquals("Sesi açtım.", backend.reply("sesi aç", runner))
        assertEquals("volume", calls.single().first)
        assertEquals("up", calls.single().second.str("action"))
        assertEquals("Rica ederim.", backend.reply("teşekkürler", runner))

        val first = server.takeRequest()
        assertEquals("Bearer sk-x", first.headers["Authorization"])
        assertTrue(first.json()["tools"]!!.jsonArray.any { it.jsonObject["type"]!!.jsonPrimitive.content == "web_search" })
        val second = server.takeRequest().json()
        assertEquals("r1", second.str("previous_response_id"))
        val output = second["input"]!!.jsonArray.single().jsonObject
        assertEquals("function_call_output", output.str("type"))
        assertEquals("c1", output.str("call_id"))
        assertEquals("r2", server.takeRequest().json().str("previous_response_id"))
    }

    @Test
    fun openAiCarriesOnWithoutWebSearchWhenTheKeyLacksIt() = runTest {
        reply("""{"error":{"message":"Tool 'web_search' is not supported with this model."}}""", code = 400)
        reply("""{"id":"r1","output":[{"type":"message","content":[{"type":"output_text","text":"Merhaba."}]}]}""")
        assertEquals("Merhaba.", OpenAiBackend("k", "m", ASSISTANT_TOOLS, base).reply("selam", runner))
        server.takeRequest()
        assertFalse(server.takeRequest().json()["tools"]!!.jsonArray.any { it.jsonObject["type"]!!.jsonPrimitive.content == "web_search" })
    }

    @Test
    fun geminiAnswersFunctionCallsThroughInteractions() = runTest {
        reply("""{"id":"i1","steps":[{"type":"function_call","id":"f1","name":"radio_tune","arguments":{"frequency_mhz":98.4}}]}""")
        reply("""{"id":"i2","steps":[{"type":"model_output","content":[{"type":"text","text":"98 nokta 4 açıldı."}]}]}""")
        assertEquals("98 nokta 4 açıldı.", GeminiBackend("AIza", "gemini-3.8-flash", ASSISTANT_TOOLS, base).reply("radyo 98.4", runner))
        val first = server.takeRequest()
        assertEquals("/v1beta/interactions", first.url.encodedPath)
        assertEquals("AIza", first.headers["x-goog-api-key"])
        val tools = first.json()["tools"]!!.jsonArray.map { it.jsonObject }
        assertTrue(tools.any { it.str("type") == "google_search" })
        assertFalse(tools.first { it["name"]?.jsonPrimitive?.content == "radio_tune" }["parameters"]!!.jsonObject.containsKey("additionalProperties"))
        val second = server.takeRequest().json()
        assertEquals("i1", second.str("previous_interaction_id"))
        val result = second["input"]!!.jsonArray.single().jsonObject
        assertEquals("function_result", result.str("type"))
        assertEquals("f1", result.str("call_id"))
    }
}
