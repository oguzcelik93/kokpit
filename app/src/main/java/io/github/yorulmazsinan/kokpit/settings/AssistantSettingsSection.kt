package io.github.yorulmazsinan.kokpit.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.assistant.AiException
import io.github.yorulmazsinan.kokpit.assistant.AiProvider
import io.github.yorulmazsinan.kokpit.assistant.AssistantSettings
import io.github.yorulmazsinan.kokpit.assistant.VoiceStatus
import io.github.yorulmazsinan.kokpit.assistant.backendFor
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.SettingsPicker
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.launch

fun LazyListScope.assistantSettings(container: AppContainer, assistant: AssistantSettings, update: ((AssistantSettings) -> AssistantSettings) -> Unit) {
    item { SectionHeader(stringResource(R.string.settings_assistant)) }
    item { HelpText(stringResource(R.string.settings_ai_provider_help)) }
    item {
        val providers = AiProvider.entries
        SettingsPicker(providers, assistant.provider, { provider -> update { it.copy(provider = provider) } }) { it.label }
    }
    item(key = "ai_key_${assistant.provider}") {
        val stored = assistant.keys[assistant.provider].orEmpty()
        var key by remember(stored) { mutableStateOf(stored) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                key, { key = it.trim() }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.settings_ai_key) + " · " + assistant.provider.label) },
                placeholder = { Text(assistant.provider.keyHint) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(
                shape = KokpitButtonShape,
                onClick = { update { it.copy(keys = it.keys + (it.provider to key)) } },
                enabled = key != stored,
                modifier = Modifier.heightIn(min = Dimens.MinTouch),
            ) { Text(stringResource(if (key == stored && stored.isNotEmpty()) R.string.settings_ai_key_saved else R.string.save)) }
        }
    }
    item(key = "ai_model_${assistant.provider}") { ModelRow(assistant, update) }
    item {
        ToggleRow(stringResource(R.string.settings_ai_keep_listening), assistant.keepListening, stringResource(R.string.settings_ai_keep_listening_help)) { on ->
            update { it.copy(keepListening = on) }
        }
    }
    item {
        val context = LocalContext.current
        val voice by produceState<VoiceStatus?>(null) { value = container.assistant.output.status() }
        val label = when (voice) {
            null -> "…"
            VoiceStatus.READY -> stringResource(R.string.settings_ai_voice_ready)
            VoiceStatus.MISSING_TURKISH -> stringResource(R.string.settings_ai_voice_missing)
            VoiceStatus.NO_ENGINE -> stringResource(R.string.settings_ai_voice_none)
        }
        ListRow(stringResource(R.string.settings_ai_voice), label) {
            if (voice != VoiceStatus.READY) safeStart(context, container.assistant.output.installIntent())
        }
    }
    item { ListRow(stringResource(R.string.settings_ai_test)) { container.assistant.open() } }
}

@Composable
private fun ModelRow(assistant: AssistantSettings, update: ((AssistantSettings) -> AssistantSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var model by remember(assistant.model) { mutableStateOf(assistant.model) }
    var picking by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(value: String) = update { it.copy(models = it.models + (it.provider to value.trim())) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            model, { model = it }, Modifier.weight(1f),
            label = { Text(stringResource(R.string.settings_ai_model)) },
            placeholder = { Text(assistant.provider.defaultModel) },
            singleLine = true,
        )
        if (model != assistant.model) {
            Button(shape = KokpitButtonShape, onClick = { save(model) }, modifier = Modifier.heightIn(min = Dimens.MinTouch)) { Text(stringResource(R.string.save)) }
        }
        OutlinedButton(
            shape = KokpitButtonShape,
            onClick = {
                val key = assistant.key ?: return@OutlinedButton
                picking = true
                models = null
                error = null
                scope.launch {
                    try {
                        models = backendFor(assistant, key).listModels()
                    } catch (e: AiException) {
                        error = e.spoken
                    }
                }
            },
            enabled = assistant.key != null,
            modifier = Modifier.heightIn(min = Dimens.MinTouch),
        ) { Text(stringResource(R.string.settings_ai_models)) }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.assistant_close)) } },
            title = { Text(stringResource(R.string.settings_ai_models)) },
            text = {
                val list = models
                when {
                    error != null -> Text(error.orEmpty())
                    list == null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.settings_ai_models_loading))
                    }
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list) { id ->
                            Text(
                                id,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (id == assistant.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    save(id)
                                    picking = false
                                }.padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
