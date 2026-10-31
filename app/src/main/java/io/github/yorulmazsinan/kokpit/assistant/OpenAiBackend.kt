package io.github.yorulmazsinan.kokpit.assistant

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** OpenAI Responses API; the conversation continues through `previous_response_id`. */
class OpenAiBackend(
    private val key: String,
    private val model: String,
    private val tools: List<ToolSpec>,
    private val baseUrl: String = "https://api.openai.com",
) : AiBackend {
    private var previousId: String? = null
    private var webSearch = true

    private val headers get() = mapOf("Authorization" to "Bearer $key")

    override suspend fun reply(userText: String, runTool: ToolRunner): String {
        var input: JsonElement = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", userText)
            })
        }
        repeat(MAX_TOOL_ROUNDS) {
            val response = send(input)
            previousId = response["id"]?.jsonPrimitive?.contentOrNull ?: previousId
            val output = response["output"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            val calls = output.filter { it.type == "function_call" }
            if (calls.isEmpty()) {
                if (response["status"]?.jsonPrimitive?.contentOrNull == "incomplete" && openAiText(output).isBlank()) {
                    return "Cevap yarım kaldı, tekrar sorar mısınız?"
                }
                return openAiText(output).ifBlank { "Bir cevap oluşturamadım." }
            }
            input = buildJsonArray {
                calls.forEach { call ->
                    val args = runCatching { aiJson.parseToJsonElement(call.string("arguments")).jsonObject }.getOrNull()
                    val result = if (args == null) "Error: arguments were not valid JSON." else runTool(call.string("name"), args)
                    add(buildJsonObject {
                        put("type", "function_call_output")
                        put("call_id", call.string("call_id"))
                        put("output", result)
                    })
                }
            }
        }
        return "Bu iş beklediğimden uzun sürdü, isteği biraz daha kısa söyler misiniz?"
    }

    private suspend fun send(input: JsonElement): JsonObject = try {
        httpJson(baseUrl + PATH, headers, request(input))
    } catch (e: AiException) {
        // Some keys or models lack the built-in web search; carry on without it.
        if (webSearch && e.mentions("web_search")) {
            webSearch = false
            httpJson(baseUrl + PATH, headers, request(input))
        } else {
            throw e
        }
    }

    private fun request(input: JsonElement) = buildJsonObject {
        put("model", model)
        put("instructions", ASSISTANT_SYSTEM_PROMPT)
        put("input", input)
        previousId?.let { put("previous_response_id", it) }
        put("tools", buildJsonArray {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("type", "function")
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameters)
                    put("strict", true)
                })
            }
            if (webSearch) add(buildJsonObject { put("type", "web_search") })
        })
    }

    override suspend fun listModels(): List<String> =
        httpJson("$baseUrl/v1/models", headers)["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            ?.filter { it.startsWith("gpt") || it.startsWith("o") }
            ?.sortedDescending().orEmpty()

    private companion object {
        const val PATH = "/v1/responses"
    }
}

/** Text of the assistant message items (`output_text` parts). */
fun openAiText(output: List<JsonObject>): String =
    output.filter { it.type == "message" }
        .flatMap { it["content"]?.jsonArray?.map { part -> part.jsonObject }.orEmpty() }
        .filter { it.type == "output_text" }
        .joinToString("") { it.string("text") }
        .trim()

