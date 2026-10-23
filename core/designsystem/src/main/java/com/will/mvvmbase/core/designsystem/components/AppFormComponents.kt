package com.will.mvvmbase.core.designsystem.components

import com.will.mvvmbase.core.designsystem.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    TopAppBar(title = { Text(title) }, navigationIcon = {
        onBack?.let { TextButton(onClick = it) { Text(resources.getString(R.string.ui_appformcomponents_3)) } }
    })
}

@Composable
fun AppSubmitButton(label: String, submitting: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled && !submitting, modifier = modifier) {
        if (submitting) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun AppFormField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, error: String? = null, enabled: Boolean = true) {
    OutlinedTextField(value, onValueChange, modifier = modifier, label = { Text(label) },
        singleLine = true, enabled = enabled, isError = error != null,
        supportingText = error?.let { message -> { Text(message) } })
}

@Composable
fun AppListFooter(loading: Boolean, error: String?, onRetry: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    when {
        loading -> AppLoadingState(resources.getString(R.string.ui_appformcomponents_1))
        error != null -> AppErrorState(error, onRetry = onRetry)
    }
}
