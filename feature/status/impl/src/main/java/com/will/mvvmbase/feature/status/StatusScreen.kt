package com.will.mvvmbase.feature.status

import com.will.mvvmbase.feature.status.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.will.mvvmbase.core.designsystem.components.AppPageStatus
import com.will.mvvmbase.core.designsystem.components.AppPullToRefreshBox
import com.will.mvvmbase.core.designsystem.components.AppStateContent
import com.will.mvvmbase.feature.status.domain.StatusResult

@Composable
internal fun StatusRoute(onBack: () -> Unit) {
    val viewModel: StatusViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatusScreen(state, viewModel::check, onBack)
}

@Composable
internal fun StatusScreen(state: StatusUiState, onCheck: () -> Unit, onBack: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    Scaffold { padding ->
        AppPullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onCheck,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                TextButton(onClick = onBack) { Text(resources.getString(R.string.ui_statusscreen_1)) }
                Text(resources.getString(R.string.ui_statusscreen_2), style = MaterialTheme.typography.headlineMedium)
                Text(resources.getString(R.string.ui_statusscreen_3))
                val result = state.result
                val pageStatus = when {
                    result == null && state.isLoading -> AppPageStatus.Loading
                    result == null -> AppPageStatus.Empty
                    result is StatusResult.Available -> AppPageStatus.Content
                    result is StatusResult.HttpFailure -> result.statusCode.toPageStatus()
                    result == StatusResult.NetworkFailure -> AppPageStatus.NoNetwork
                    result == StatusResult.Timeout -> AppPageStatus.Timeout
                    result == StatusResult.UnexpectedFailure -> AppPageStatus.Error
                    result == StatusResult.NotConfigured -> AppPageStatus.ConfigurationRequired
                    else -> AppPageStatus.Error
                }
                val canRetry = when (result) {
                    null -> !state.isLoading
                    StatusResult.NetworkFailure,
                    StatusResult.Timeout,
                    StatusResult.UnexpectedFailure -> true
                    is StatusResult.HttpFailure -> result.statusCode == 408 ||
                        result.statusCode == 429 || result.statusCode >= 500
                    else -> false
                }
                val title = when {
                    result is StatusResult.HttpFailure && pageStatus == AppPageStatus.Error ->
                        resources.getString(R.string.status_http_title, result.statusCode)
                    else -> null
                }
                val description = when (result) {
                    null -> if (state.isLoading) resources.getString(R.string.ui_statusscreen_4) else resources.getString(R.string.ui_statusscreen_5)
                    is StatusResult.Available -> null
                    is StatusResult.HttpFailure -> resources.getString(R.string.status_http_description, result.statusCode)
                    StatusResult.NetworkFailure -> resources.getString(R.string.ui_statusscreen_6)
                    StatusResult.Timeout -> resources.getString(R.string.ui_statusscreen_7)
                    StatusResult.UnexpectedFailure -> resources.getString(R.string.ui_statusscreen_8)
                    StatusResult.NotConfigured -> resources.getString(R.string.ui_statusscreen_9)
                }
                AppStateContent(
                    status = pageStatus,
                    title = title ?: if (result == null && !state.isLoading) resources.getString(R.string.ui_statusscreen_10) else null,
                    description = description,
                    actionLabel = when {
                        result == null && !state.isLoading -> resources.getString(R.string.ui_statusscreen_11)
                        canRetry -> resources.getString(R.string.ui_statusscreen_12)
                        else -> null
                    },
                    onAction = if (canRetry) onCheck else null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (result is StatusResult.Available) {
                        Text(resources.getString(R.string.status_server_state, result.status))
                    }
                }
            }
        }
    }
}

private fun Int.toPageStatus(): AppPageStatus = when (this) {
    401 -> AppPageStatus.Unauthorized
    403 -> AppPageStatus.Forbidden
    404 -> AppPageStatus.NotFound
    408 -> AppPageStatus.Timeout
    429 -> AppPageStatus.RateLimited
    in 500..599 -> AppPageStatus.ServerError
    else -> AppPageStatus.Error
}
