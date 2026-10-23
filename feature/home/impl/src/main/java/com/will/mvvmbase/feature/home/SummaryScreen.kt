package com.will.mvvmbase.feature.home

import com.will.mvvmbase.feature.home.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
internal fun SummaryRoute(onBack: () -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SummaryScreen(state, onBack)
}

@Composable
internal fun SummaryScreen(state: HomeUiState, onBack: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(resources.getString(R.string.ui_summaryscreen_1)) }
            Text(resources.getString(R.string.ui_summaryscreen_2), style = MaterialTheme.typography.headlineMedium)
            if (state.errorMessageRes != null) {
                Text(resources.getString(state.errorMessageRes), color = MaterialTheme.colorScheme.error)
            } else if (state.isLoading) {
                Text(resources.getString(R.string.ui_summaryscreen_3))
            } else {
                Text(resources.getString(R.string.saved_count, state.taskCount), style = MaterialTheme.typography.titleLarge)
            }
            Text(resources.getString(R.string.ui_summaryscreen_4))
        }
    }
}

