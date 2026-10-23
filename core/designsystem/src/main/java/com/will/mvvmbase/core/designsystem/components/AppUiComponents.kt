package com.will.mvvmbase.core.designsystem.components

import com.will.mvvmbase.core.designsystem.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope

@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    indicator: @Composable BoxScope.() -> Unit = {
        PullToRefreshDefaults.Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            isRefreshing = isRefreshing,
            state = state,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    },
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = indicator,
        content = content,
    )
}

@Composable
fun AppLoadingState(
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(message ?: androidx.compose.ui.res.stringResource(R.string.ui_appuicomponents_1),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AppEmptyState(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun AppErrorState(
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        if (retryLabel != null && onRetry != null) {
            Button(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

/** Common full-width screen states for loading, empty data, connectivity, access, and server errors. */
enum class AppPageStatus {
    Content,
    Loading,
    Empty,
    NoNetwork,
    Timeout,
    PermissionRequired,
    Unauthorized,
    Forbidden,
    NotFound,
    RateLimited,
    ServerError,
    ConfigurationRequired,
    Error,
}

@Composable
fun AppStateContent(
    status: AppPageStatus,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (status == AppPageStatus.Content) {
        content()
    } else {
        AppStatePage(
            status = status,
            modifier = modifier,
            title = title,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}

@Composable
fun AppStatePage(
    status: AppPageStatus,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val (defaultTitle, defaultDescription) = when (status) {
        AppPageStatus.Content -> "" to null
        AppPageStatus.Loading -> resources.getString(R.string.ui_appuicomponents_1) to null
        AppPageStatus.Empty -> resources.getString(R.string.ui_appuicomponents_2) to null
        AppPageStatus.NoNetwork -> resources.getString(R.string.ui_appuicomponents_3) to resources.getString(R.string.ui_appuicomponents_4)
        AppPageStatus.Timeout -> resources.getString(R.string.ui_appuicomponents_5) to resources.getString(R.string.ui_appuicomponents_6)
        AppPageStatus.PermissionRequired -> resources.getString(R.string.ui_appuicomponents_7) to resources.getString(R.string.ui_appuicomponents_8)
        AppPageStatus.Unauthorized -> resources.getString(R.string.ui_appuicomponents_9) to resources.getString(R.string.ui_appuicomponents_10)
        AppPageStatus.Forbidden -> resources.getString(R.string.ui_appuicomponents_11) to resources.getString(R.string.ui_appuicomponents_12)
        AppPageStatus.NotFound -> resources.getString(R.string.ui_appuicomponents_13) to resources.getString(R.string.ui_appuicomponents_14)
        AppPageStatus.RateLimited -> resources.getString(R.string.ui_appuicomponents_15) to resources.getString(R.string.ui_appuicomponents_16)
        AppPageStatus.ServerError -> resources.getString(R.string.ui_appuicomponents_17) to resources.getString(R.string.ui_appuicomponents_18)
        AppPageStatus.ConfigurationRequired -> resources.getString(R.string.ui_appuicomponents_19) to resources.getString(R.string.ui_appuicomponents_20)
        AppPageStatus.Error -> resources.getString(R.string.ui_appuicomponents_21) to resources.getString(R.string.ui_appuicomponents_22)
    }
    val resolvedTitle = title ?: defaultTitle
    val resolvedDescription = description ?: defaultDescription

    Box(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 180.dp).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status == AppPageStatus.Loading) {
                CircularProgressIndicator()
            }
            if (resolvedTitle.isNotEmpty()) {
                Text(
                    text = resolvedTitle,
                    color = if (status == AppPageStatus.Error || status == AppPageStatus.ServerError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (resolvedDescription != null) {
                Text(
                    text = resolvedDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text(dismissLabel ?: androidx.compose.ui.res.stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}

