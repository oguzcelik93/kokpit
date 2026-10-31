package io.github.yorulmazsinan.kokpit.assistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Anthropic Messages API. Adaptive thinking at low effort keeps spoken answers quick; web
 * search runs server-side; `fallbacks: "default"` lets a declined request retry on the model
 * Anthropic recommends instead of failing.
 */
class ClaudeBackend(
    private val key: String,
    private val model: String,
    private val tools: List<ToolSpec>,
    private val baseUrl: String = "https://api.anthropic.com",
) : AiBackend {
    private val messages = mutableListOf<JsonObject>()

    private val headers get() = mapOf(
        "x-api-key" to key,
        "anthropic-version" to API_VERSION,
        "anthropic-beta" to FALLBACK_BETA,
    )

    override suspend fun reply(userText: String, runTool: ToolRunner): String {
        messages += buildJsonObject {
            put("role", "user")
            put("content", userText)
        }
        repeat(MAX_TOOL_ROUNDS) {
            val response = httpJson(baseUrl + PATH, headers, request())
            val content = response["content"]?.jsonArray ?: JsonArray(emptyList())
            val stop = response["stop_reason"]?.jsonPrimitive?.contentOrNull
            if (stop == "refusal") {
                // Leave no half turn behind: the next question starts clean.
                messages.removeAt(messages.lastIndex)
                return "Bu isteğe yardımcı olamıyorum."
            }
            // Thinking and server-tool blocks go back unchanged on the next request.
            messages += buildJsonObject {
                put("role", "assistant")
                put("content", content)
            }
            when (stop) {
                "tool_use" -> {
                    val results = buildJsonArray {
                        content.map { it.jsonObject }.filter { it.type == "tool_use" }.forEach { block ->
                            val output = runTool(block.string("name"), block["input"]?.jsonObject ?: JsonObject(emptyMap()))
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", block.string("id"))
                                put("content", output)
                                if (output.startsWith("Error:")) put("is_error", true)
                            })
                        }
                    }
                    messages += buildJsonObject {
                        put("role", "user")
                        put("content", results)
                    }
                }
                // The server paused a long web search; sending the turn back resumes it.
                "pause_turn" -> Unit
                else -> return claudeText(content).ifBlank { "Bir cevap oluşturamadım." }
            }
        }
        return "Bu iş beklediğimden uzun sürdü, isteği biraz daha kısa söyler misiniz?"
    }

    private fun request() = buildJsonObject {
        put("model", model)
        put("max_tokens", 8000)
        put("system", ASSISTANT_SYSTEM_PROMPT)
        putJsonObject("thinking") { put("type", "adaptive") }
        putJsonObject("output_config") { put("effort", "low") }
        put("fallbacks", "default")
        put("tools", buildJsonArray {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("input_schema", tool.parameters)
                    put("strict", true)
                })
            }
            add(buildJsonObject {
                put("type", "web_search_20260209")
                put("name", "web_search")
                put("max_uses", 3)
                putJsonObject("user_location") {
                    put("type", "approximate")
                    put("country", "TR")
                    put("timezone", "Europe/Istanbul")
                }
            })
        })
        put("messages", JsonArray(messages))
    }

    override suspend fun listModels(): List<String> =
        httpJson("$baseUrl/v1/models?limit=100", headers - "anthropic-beta")["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.orEmpty()

    private companion object {
        const val PATH = "/v1/messages"
        const val API_VERSION = "2026-10-21"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}

/** The spoken answer: every text block, in order (web search splits text around citations). */
fun claudeText(content: JsonArray): String =
    content.map { it.jsonObject }.filter { it.type == "text" }.joinToString("") { it.string("text") }.trim()

internal val JsonObject.type: String? get() = this["type"]?.jsonPrimitive?.contentOrNull
internal fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

