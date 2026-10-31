package io.github.yorulmazsinan.kokpit.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.storage.exportDirs
import io.github.yorulmazsinan.kokpit.storage.writeFirst
import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        DiagnosticsCollector(context).collect().toText() +
            (readCrashLog(context)?.let { "\n[last crashes]\n$it" } ?: "") +
            "\n[media events]\n" + io.github.yorulmazsinan.kokpit.media.MediaEventLog.shared.lines().joinToString("\n") +
            "\n\n[head unit]\n" + (context.applicationContext as io.github.yorulmazsinan.kokpit.KokpitApp).container.headUnit.probe().joinToString("\n") +
            "\n\n[power events]\n" + io.github.yorulmazsinan.kokpit.connect.PowerEventLog.lines(context).joinToString("\n")
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.diagnostics_saved else R.string.diagnostics_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val scope = rememberCoroutineScope()
    var lastResult by remember { mutableStateOf<String?>(null) }
    fun show(message: String) {
        lastResult = message
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch {
            val file = withContext(Dispatchers.IO) { writeFirst(exportDirs(context), "kokpit-diagnostics.txt", text) }
            show(file?.let { context.getString(R.string.saved_to, it.absolutePath) } ?: context.getString(R.string.diagnostics_save_failed))
        }
    }
    val vendorPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { exportVendorApks(context, exportDirs(context)) }
            show(result?.let { (dir, n) -> context.getString(R.string.vendor_export_done, n, dir.absolutePath) } ?: context.getString(R.string.vendor_export_failed))
        }
    }
    ScreenScaffold(stringResource(R.string.diagnostics_title), onBack) {
        lastResult?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(shape = KokpitButtonShape, onClick = { copyToClipboard(context, text) }, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                Text(stringResource(R.string.diagnostics_copy))
            }
            OutlinedButton(shape = KokpitButtonShape, onClick = { writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }, modifier = Modifier.heightIn(min = Dimens.MinTouch)) {
                Text(stringResource(R.string.diagnostics_save))
            }
        }
        OutlinedButton(
            shape = KokpitButtonShape,
            onClick = { vendorPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = Dimens.MinTouch),
        ) { Text(stringResource(R.string.vendor_export)) }
        SelectionContainer {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kokpit diagnostics", text))
    Toast.makeText(context, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
}
