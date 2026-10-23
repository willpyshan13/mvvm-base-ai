package com.will.mvvmbase.feature.auth

import com.will.mvvmbase.feature.auth.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun LoginRoute(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == LoginEvent.Succeeded) onLoginSuccess()
        }
    }

    LoginScreen(
        state = state,
        onLogin = viewModel::login,
        onBack = onBack,
    )
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    var username by rememberSaveable { mutableStateOf("") }
    // Credentials should not be written to the activity's saved-state bundle.
    var password by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(resources.getString(R.string.ui_loginscreen_1), style = MaterialTheme.typography.headlineMedium)
            Text(resources.getString(R.string.ui_loginscreen_2))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(resources.getString(R.string.ui_loginscreen_3)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(resources.getString(R.string.ui_loginscreen_4)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessageRes?.let { message ->
                Text(resources.getString(message), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { onLogin(username, password) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) resources.getString(R.string.ui_loginscreen_5) else resources.getString(R.string.ui_loginscreen_6))
            }
            TextButton(onClick = onBack) { Text(resources.getString(R.string.ui_loginscreen_7)) }
        }
    }
}
