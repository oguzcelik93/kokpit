package io.github.yorulmazsinan.kokpit.assistant

/** The AI services the assistant can use, each with the user's own API key. */
enum class AiProvider(val label: String, val defaultModel: String, val keyHint: String) {
    CLAUDE("Claude", "claude-opus-5-5", "sk-ant-…"),
    OPENAI("OpenAI", "gpt-6-astra", "sk-…"),
    GEMINI("Gemini", "gemini-3.8-flash", "AIza…"),
}

data class AssistantSettings(
    val provider: AiProvider = AiProvider.CLAUDE,
    val keys: Map<AiProvider, String> = emptyMap(),
    val models: Map<AiProvider, String> = emptyMap(),
    /** After answering, listen once more for a follow-up. */
    val keepListening: Boolean = true,
) {
    val key: String? get() = keys[provider]?.takeIf { it.isNotBlank() }
    val model: String get() = models[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
}
