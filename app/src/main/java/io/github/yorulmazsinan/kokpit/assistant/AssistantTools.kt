package io.github.yorulmazsinan.kokpit.assistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A device action offered to every provider with the same name, description and JSON schema. */
data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

/** What the assistant can do in the car. Each returns a short result for the model to report. */
interface AssistantActions {
    fun openApp(name: String): String
    fun media(action: String): String
    fun navigate(destination: String): String
    fun tuneRadio(tenths: Int): String
    fun call(query: String): String
    fun volume(action: String): String
    fun status(): String
    fun connectPhoneWifi(): String
}

private fun schema(vararg props: Pair<String, JsonObject>) = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") { props.forEach { (name, def) -> put(name, def) } }
    putJsonArray("required") { props.forEach { add(JsonPrimitive(it.first)) } }
    put("additionalProperties", false)
}

private fun string(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun choice(description: String, vararg values: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
    put("enum", JsonArray(values.map(::JsonPrimitive)))
}

val ASSISTANT_TOOLS = listOf(
    ToolSpec(
        "open_app",
        "Opens a screen or app on the car's head unit: CarPlay, Radio, Bluetooth music, Phone, Maps, USB media, Settings, Quick settings, Home, or any installed app by name.",
        schema("name" to string("Screen or app name as the driver said it, e.g. \"radyo\", \"CarPlay\", \"YouTube\".")),
    ),
    ToolSpec(
        "media_control",
        "Controls whatever is playing (phone music over Bluetooth, USB media, streaming apps).",
        schema("action" to choice("What to do.", "play", "pause", "next", "previous")),
    ),
    ToolSpec(
        "navigate",
        "Starts turn-by-turn navigation in the car's maps app to a place name or address. For the driver's saved home use exactly \"ev\", for their saved work place exactly \"iş\" (\"eve götür\", \"işe gidelim\", \"ofise\").",
        schema("destination" to string("Place name or address, as specific as the driver gave it; \"ev\" or \"iş\" for the saved places.")),
    ),
    ToolSpec(
        "radio_tune",
        "Switches to the FM radio and tunes to a frequency between 87.5 and 108.0 MHz.",
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("frequency_mhz") {
                    put("type", "number")
                    put("description", "FM frequency in MHz, e.g. 98.4.")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("frequency_mhz")) }
            put("additionalProperties", false)
        },
    ),
    ToolSpec(
        "call_contact",
        "Calls a contact (or a spoken phone number) through the phone paired over Bluetooth. Returns who is being called, or the candidates when the name is ambiguous - then ask which one.",
        schema("query" to string("Contact name or phone number exactly as the driver said it.")),
    ),
    ToolSpec(
        "volume",
        "Turns the car's volume up or down a step, or toggles mute.",
        schema("action" to choice("What to do.", "up", "down", "mute")),
    ),
    ToolSpec(
        "car_status",
        "Returns what the dashboard knows right now: time and date, speed, average speed, heading, weather, next calendar event, what is playing, radio frequency, connected phone and Wi-Fi.",
        schema(),
    ),
    ToolSpec(
        "connect_phone_wifi",
        "Connects the head unit to the driver's phone hotspot (the network saved in Kokpit settings).",
        schema(),
    ),
)

/** Runs one tool call; bad arguments come back as a message the model can recover from. */
fun runTool(name: String, args: JsonObject, actions: AssistantActions): String = runCatching {
    fun text(key: String) = args[key]?.jsonPrimitive?.content?.trim().orEmpty()
    when (name) {
        "open_app" -> actions.openApp(text("name"))
        "media_control" -> actions.media(text("action"))
        "navigate" -> actions.navigate(text("destination"))
        "radio_tune" -> {
            val mhz = args["frequency_mhz"]?.jsonPrimitive?.doubleOrNull ?: text("frequency_mhz").replace(',', '.').toDoubleOrNull()
            val tenths = mhz?.let { Math.round(it * 10).toInt() }
            if (tenths == null || tenths !in 875..1080) "Error: frequency must be between 87.5 and 108.0 MHz." else actions.tuneRadio(tenths)
        }
        "call_contact" -> actions.call(text("query"))
        "volume" -> actions.volume(text("action"))
        "car_status" -> actions.status()
        "connect_phone_wifi" -> actions.connectPhoneWifi()
        else -> "Error: unknown tool $name."
    }
}.getOrElse { "Error: ${it.message ?: it.javaClass.simpleName}" }

/** Spoken replies: short, plain Turkish, no formatting a voice cannot read. */
val ASSISTANT_SYSTEM_PROMPT = """
Sen Kokpit'sin: bir otomobilin multimedya ekranında çalışan sesli asistan. Cevapların sesli okunuyor ve sürücü çoğu zaman yolda, bu yüzden:
- Türkçe, doğal ve kısa konuş: genellikle bir iki cümle. Uzun bilgi istenirse en önemlisini söyle, gerekirse devamını sor.
- Madde işareti, başlık, tablo, emoji, bağlantı ya da markdown kullanma; rakamları ve birimleri okunur biçimde yaz (ör. "98 nokta 4", "21 derece").
- Araçta bir şey yapılması istenirse ilgili aracı kullan ve yaptığını tek cümleyle söyle. Araç bir hata döndürürse nedenini kısaca söyle.
- Hız, hava, saat, takvim, çalan şarkı gibi sorular için car_status aracını kullan; uydurma.
- Güncel bilgi gereken sorularda (haber, maç, trafik, açık bir mekan) web aramasını kullan.
- Bir kişiyi aramadan önce adı net değilse hangisi olduğunu sor; yalnızca sürücü açıkça istediğinde ara.
- Sürüş güvenliğini riske atacak bir şey önerme; ekrana uzun süre bakmayı gerektiren işleri durunca yapmayı öner.
""".trimIndent()
