package com.will.mvvmbase.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.will.mvvmbase.core.designsystem.R
import androidx.compose.ui.res.stringResource

@Composable
fun AppInfoDialog(title: String, message: String, onDismiss: () -> Unit, closeLabel: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) {
            Text(closeLabel ?: stringResource(R.string.dialog_close))
        } },
    )
}

@Composable
fun AppInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    fieldLabel: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean,
    enabled: Boolean = true,
    dismissLabel: String? = null,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        modifier = Modifier.imePadding(),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = value, onValueChange = onValueChange,
                    enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(fieldLabel) }, isError = errorMessage != null)
                if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled && confirmEnabled) {
            Text(confirmLabel)
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) {
            Text(dismissLabel ?: stringResource(R.string.dialog_cancel))
        } },
    )
}

data class AppChoice<T>(val value: T, val label: String)

@Composable
fun <T> AppChoiceDialog(
    title: String,
    choices: List<AppChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    Row(Modifier.fillMaxWidth().selectable(
                        selected = choice.value == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice.value) },
                    ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice.value == selected, onClick = null)
                        Text(choice.label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
fun AppProgressDialog(message: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(
        dismissOnBackPress = false, dismissOnClickOutside = false,
    )) {
        Surface(shape = MaterialTheme.shapes.large) {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(message)
            }
        }
    }
}
