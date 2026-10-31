package io.github.yorulmazsinan.kokpit.assistant

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

typealias ToolRunner = suspend (name: String, args: JsonObject) -> String

/**
 * One conversation with one provider. Plain REST over OkHttp: the vendor SDKs could not be
 * checked on this Android 8.1 unit, and one small HTTP layer keeps the three alike.
 */
interface AiBackend {
    /** Sends what the driver said, runs any tool calls, and returns the text to speak. */
    suspend fun reply(userText: String, runTool: ToolRunner): String

    /** Model ids the key can use, for the settings picker. */
    suspend fun listModels(): List<String>
}

/** A failure with a sentence fit to be spoken, plus detail for diagnostics. */
class AiException(val spoken: String, val detail: String? = null, cause: Throwable? = null) : Exception(detail ?: spoken, cause)

/** Tool-call rounds per answer before giving up (a model stuck calling tools). */
const val MAX_TOOL_ROUNDS = 6

val aiJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

val aiHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .build()

/** Turkish sentence for an HTTP failure. */
fun spokenError(code: Int): String = when (code) {
    401, 403 -> "API anahtarı kabul edilmedi. Ayarlardan anahtarı kontrol edin."
    404 -> "Seçilen model bulunamadı. Ayarlardan başka bir model seçin."
    402, 429 -> "Yapay zekâ hesabının kullanım sınırı doldu ya da çok sık istek gitti. Biraz sonra tekrar deneyin."
    in 500..599 -> "Yapay zekâ servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin."
    else -> "Yapay zekâ isteği tamamlanamadı."
}

suspend fun httpJson(url: String, headers: Map<String, String>, body: JsonObject? = null, http: OkHttpClient = aiHttp): JsonObject =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (body != null) builder.post(aiJson.encodeToString(JsonElement.serializer(), body).toRequestBody("application/json".toMediaType()))
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw AiException("İnternete ulaşamadım. Telefonun internet paylaşımını kontrol edin.", e.message, e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AiException(spokenError(it.code), "HTTP ${it.code}: ${text.take(400)}")
            runCatching { aiJson.parseToJsonElement(text).jsonObject }
                .getOrElse { e -> throw AiException("Yapay zekâdan anlaşılmayan bir cevap geldi.", text.take(400), e) }
        }
    }

/** True when a 400 complains about a tool we can retry without (e.g. web search not enabled). */
fun AiException.mentions(vararg words: String): Boolean = detail?.let { d -> d.startsWith("HTTP 400") && words.any { d.contains(it, ignoreCase = true) } } == true

fun backendFor(settings: AssistantSettings, key: String, tools: List<ToolSpec> = ASSISTANT_TOOLS): AiBackend = when (settings.provider) {
    AiProvider.CLAUDE -> ClaudeBackend(key, settings.model, tools)
    AiProvider.OPENAI -> OpenAiBackend(key, settings.model, tools)
    AiProvider.GEMINI -> GeminiBackend(key, settings.model, tools)
}
