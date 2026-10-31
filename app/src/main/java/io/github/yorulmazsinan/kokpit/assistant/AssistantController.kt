package io.github.yorulmazsinan.kokpit.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SetupProblem { NO_KEY, NO_MIC_PERMISSION, NO_RECOGNIZER }

sealed interface AssistantState {
    data object Hidden : AssistantState
    data class NeedsSetup(val problem: SetupProblem) : AssistantState
    data class Listening(val partial: String, val followUp: Boolean = false) : AssistantState
    data class Thinking(val heard: String) : AssistantState
    data class Answer(val heard: String?, val reply: String, val speaking: Boolean, val isError: Boolean = false, val detail: String? = null) : AssistantState
}

/**
 * One press of the mic button: listen, ask the chosen AI, run what it asks for, speak the
 * answer, and (if enabled) listen once more for a follow-up. A conversation lasts until the
 * panel closes, so "and tomorrow?" still makes sense.
 */
class AssistantController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: () -> AssistantSettings,
    private val actions: AssistantActions,
) {
    private val _state = MutableStateFlow<AssistantState>(AssistantState.Hidden)
    val state: StateFlow<AssistantState> = _state.asStateFlow()
    private val input by lazy { SpeechInput(context) }
    val output by lazy { SpeechOutput(context) }
    private val focus by lazy { ListeningFocus(context) }
    private var job: Job? = null
    private var backend: AiBackend? = null
    private var backendFor: AssistantSettings? = null

    /** Opens the panel and starts listening (or says what is missing). */
    fun open() {
        job?.cancel()
        output.stop()
        job = scope.launch(Dispatchers.Main) { session() }
    }

    fun close() {
        job?.cancel()
        job = null
        output.stop()
        backend = null
        _state.value = AssistantState.Hidden
    }

    private suspend fun session() {
        val s = settings()
        val key = s.key
        val problem = when {
            key == null -> SetupProblem.NO_KEY
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> SetupProblem.NO_MIC_PERMISSION
            !input.available() -> SetupProblem.NO_RECOGNIZER
            else -> null
        }
        if (problem != null || key == null) {
            _state.value = AssistantState.NeedsSetup(problem ?: SetupProblem.NO_KEY)
            return
        }
        if (backend == null || backendFor != s) {
            backend = backendFor(s, key)
            backendFor = s
        }
        var followUp = false
        while (true) {
            _state.value = AssistantState.Listening("", followUp)
            val heard = focus.hold { input.listen { partial -> _state.value = AssistantState.Listening(partial, followUp) } }
            when (heard) {
                Heard.Nothing -> {
                    if (followUp) close() else say(null, "Sizi duyamadım, tekrar dener misiniz?", isError = true)
                    return
                }
                Heard.NoPermission -> {
                    _state.value = AssistantState.NeedsSetup(SetupProblem.NO_MIC_PERMISSION)
                    return
                }
                is Heard.Failed -> {
                    say(null, heard.message, isError = true)
                    return
                }
                is Heard.Text -> {
                    if (isGoodbye(heard.text)) {
                        close()
                        return
                    }
                    _state.value = AssistantState.Thinking(heard.text)
                    val reply = try {
                        backend!!.reply(heard.text) { name, args -> withContext(Dispatchers.Main) { runTool(name, args, actions) } }
                    } catch (e: AiException) {
                        say(heard.text, e.spoken, isError = true, detail = e.detail)
                        return
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        say(heard.text, "Bir şeyler ters gitti, tekrar dener misiniz?", isError = true, detail = e.toString())
                        return
                    }
                    say(heard.text, reply)
                    if (!settings().keepListening) {
                        delay(ANSWER_LINGER_MS)
                        close()
                        return
                    }
                    followUp = true
                }
            }
        }
    }

    private suspend fun say(heard: String?, text: String, isError: Boolean = false, detail: String? = null) {
        _state.value = AssistantState.Answer(heard, text, speaking = true, isError = isError, detail = detail)
        output.speak(text)
        _state.value = AssistantState.Answer(heard, text, speaking = false, isError = isError, detail = detail)
    }

    private companion object {
        const val ANSWER_LINGER_MS = 6_000L
    }
}

/** "Tamam", "teşekkürler", "kapat" end the conversation without asking the AI. */
fun isGoodbye(text: String): Boolean = foldTurkish(text) in setOf(
    "tamam", "tamamdir", "tesekkurler", "tesekkur ederim", "sagol", "sag ol", "kapat", "iptal", "vazgec", "yok", "hayir", "gerek yok",
)
