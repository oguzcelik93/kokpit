package io.github.yorulmazsinan.kokpit.assistant

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Gemini Interactions API; the conversation continues through `previous_interaction_id`. */
class GeminiBackend(
    private val key: String,
    private val model: String,
    private val tools: List<ToolSpec>,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : AiBackend {
    private var previousId: String? = null
    private var googleSearch = true

    private val headers get() = mapOf("x-goog-api-key" to key)

    override suspend fun reply(userText: String, runTool: ToolRunner): String {
        var input: JsonElement = JsonPrimitive(userText)
        repeat(MAX_TOOL_ROUNDS) {
            val response = send(input)
            previousId = response["id"]?.jsonPrimitive?.contentOrNull ?: previousId
            val steps = response["steps"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            val calls = steps.filter { it.type == "function_call" }
            if (calls.isEmpty()) return geminiText(steps).ifBlank { "Bir cevap oluşturamadım." }
            input = buildJsonArray {
                calls.forEach { call ->
                    val args = when (val raw = call["arguments"]) {
                        is JsonObject -> raw
                        is JsonPrimitive -> runCatching { aiJson.parseToJsonElement(raw.content).jsonObject }.getOrNull()
                        else -> JsonObject(emptyMap())
                    }
                    val result = if (args == null) "Error: arguments were not valid JSON." else runTool(call.string("name"), args)
                    add(buildJsonObject {
                        put("type", "function_result")
                        put("name", call.string("name"))
                        put("call_id", call.string("id"))
                        put("result", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", result)
                            })
                        })
                    })
                }
            }
        }
        return "Bu iş beklediğimden uzun sürdü, isteği biraz daha kısa söyler misiniz?"
    }

    private suspend fun send(input: JsonElement): JsonObject = try {
        httpJson(baseUrl + PATH, headers, request(input))
    } catch (e: AiException) {
        if (googleSearch && e.mentions("google_search", "search")) {
            googleSearch = false
            httpJson(baseUrl + PATH, headers, request(input))
        } else {
            throw e
        }
    }

    private fun request(input: JsonElement) = buildJsonObject {
        put("model", model)
        put("system_instruction", ASSISTANT_SYSTEM_PROMPT)
        put("input", input)
        previousId?.let { put("previous_interaction_id", it) }
        put("tools", buildJsonArray {
            if (googleSearch) add(buildJsonObject { put("type", "google_search") })
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("type", "function")
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", geminiSchema(tool.parameters))
                })
            }
        })
    }

    override suspend fun listModels(): List<String> =
        httpJson("$baseUrl/v1beta/models?pageSize=200", headers)["models"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
            ?.filter { it.startsWith("gemini") }
            ?.sortedDescending().orEmpty()

    private companion object {
        const val PATH = "/v1beta/interactions"
    }
}

/** Gemini's schema dialect has no `additionalProperties`. */
fun geminiSchema(schema: JsonObject): JsonObject = JsonObject(schema - "additionalProperties")

/** Text of the model output steps. */
fun geminiText(steps: List<JsonObject>): String =
    steps.filter { it.type == "model_output" }
        .flatMap { it["content"]?.jsonArray?.map { part -> part.jsonObject }.orEmpty() }
        .filter { it.type == "text" }
        .joinToString("") { it.string("text") }
        .trim()
