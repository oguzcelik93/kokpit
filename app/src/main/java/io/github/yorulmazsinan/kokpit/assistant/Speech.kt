package io.github.yorulmazsinan.kokpit.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface Heard {
    data class Text(val text: String) : Heard
    /** Silence or nothing recognisable. */
    data object Nothing : Heard
    data object NoPermission : Heard
    data class Failed(val message: String) : Heard
}

/** `SpeechRecognizer` error codes as something the assistant can act on or say. */
fun heardForError(code: Int): Heard = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard.Nothing
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.NoPermission
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
        Heard.Failed("Ses tanıma için internet gerekiyor. Telefonun internet paylaşımını kontrol edin.")
    SpeechRecognizer.ERROR_AUDIO -> Heard.Failed("Mikrofondan ses alınamadı.")
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Heard.Failed("Ses tanıma meşgul, birazdan tekrar deneyin.")
    else -> Heard.Failed("Ses tanıma çalışmadı.")
}

/** Turkish speech to text through the unit's recogniser (the Google app). Main thread only. */
class SpeechInput(private val context: Context) {
    fun available(): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    suspend fun listen(onPartial: (String) -> Unit): Heard = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        fun finish(result: Heard) {
            if (!cont.isActive) return
            runCatching { recognizer.destroy() }
            cont.resume(result)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                finish(if (text.isNullOrEmpty()) Heard.Nothing else Heard.Text(text))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }

            override fun onError(error: Int) = finish(heardForError(error))
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        cont.invokeOnCancellation {
            runCatching {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { recognizer.startListening(intent) }.onFailure { finish(Heard.Failed("Ses tanıma başlatılamadı.")) }
    }
}

enum class VoiceStatus { READY, MISSING_TURKISH, NO_ENGINE }

/** Turkish text to speech, ducking whatever plays while it talks. Main thread only. */
class SpeechOutput(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = CompletableDeferred<VoiceStatus>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()

    private fun engine(): TextToSpeech = tts ?: TextToSpeech(context) { status ->
        val engine = tts
        val result = when {
            status != TextToSpeech.SUCCESS || engine == null -> VoiceStatus.NO_ENGINE
            engine.setLanguage(TURKISH) >= TextToSpeech.LANG_AVAILABLE -> VoiceStatus.READY
            else -> VoiceStatus.MISSING_TURKISH
        }
        engine?.setAudioAttributes(attributes)
        ready.complete(result)
    }.also { tts = it }

    suspend fun status(): VoiceStatus {
        engine()
        return ready.await()
    }

    /** Speaks and returns when done (or at once when there is no usable voice). */
    suspend fun speak(text: String) {
        if (status() == VoiceStatus.NO_ENGINE || text.isBlank()) return
        val engine = engine()
        audio.requestAudioFocus(focus)
        try {
            suspendCancellableCoroutine { cont ->
                val id = "kokpit-${System.nanoTime()}"
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                })
                cont.invokeOnCancellation { engine.stop() }
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
            }
        } finally {
            audio.abandonAudioFocusRequest(focus)
        }
    }

    fun stop() {
        tts?.stop()
    }

    /** Opens the system screen that installs voice data (for the Turkish voice). */
    fun installIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
    }
}

/** Holds the audio while listening so music does not drown the driver's voice. */
class ListeningFocus(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .build()

    suspend fun <T> hold(block: suspend () -> T): T {
        audio.requestAudioFocus(request)
        return try {
            block()
        } finally {
            audio.abandonAudioFocusRequest(request)
        }
    }
}
