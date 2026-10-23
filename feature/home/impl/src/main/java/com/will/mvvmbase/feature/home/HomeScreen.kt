package com.will.mvvmbase.feature.home

import com.will.mvvmbase.feature.home.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.will.mvvmbase.core.common.text.trimmedOrNull
import com.will.mvvmbase.core.designsystem.components.AppEmptyState
import com.will.mvvmbase.core.designsystem.components.AppErrorState
import com.will.mvvmbase.core.designsystem.components.AppLoadingState
import com.will.mvvmbase.core.designsystem.components.AppPullToRefreshBox
import com.will.mvvmbase.core.designsystem.components.AppInputDialog
import com.will.mvvmbase.core.designsystem.components.AppConfirmDialog
import com.will.mvvmbase.feature.home.domain.Task
import com.will.mvvmbase.feature.home.domain.TaskFilter

@Composable
internal fun HomeRoute(
    isAuthenticated: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onOpenSummary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenVideoPlayer: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val authenticated by isAuthenticated.collectAsStateWithLifecycle()
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val taskFilter by viewModel.taskFilter.collectAsStateWithLifecycle()
    val tasks = viewModel.pagedTasks.collectAsLazyPagingItems()
    HomeScreen(
        state,
        tasks,
        searchQuery,
        taskFilter,
        authenticated,
        viewModel::setSearchQuery,
        viewModel::setTaskFilter,
        viewModel::addTask,
        viewModel::removeTask,
        viewModel::updateTask,
        viewModel::setTaskCompleted,
        onOpenSummary,
        onOpenSettings,
        onOpenStatus,
        onOpenVideoPlayer,
        onLogin,
        onLogout,
    )
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    tasks: LazyPagingItems<Task>,
    searchQuery: String,
    taskFilter: TaskFilter,
    isAuthenticated: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onAddTask: (String, () -> Unit) -> Unit,
    onRemoveTask: (Long) -> Unit,
    onUpdateTask: (Long, String, () -> Unit) -> Unit,
    onSetTaskCompleted: (Long, Boolean) -> Unit,
    onOpenSummary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenVideoPlayer: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    var draft by rememberSaveable { mutableStateOf("") }
    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTitle by rememberSaveable { mutableStateOf("") }
    var deletingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    val trimmedDraft = draft.trimmedOrNull()
    val refreshState = tasks.loadState.refresh

    if (editingTaskId != null) {
        AppInputDialog(
            title = resources.getString(R.string.edit_task_title),
            value = editingTitle,
            onValueChange = { editingTitle = it },
            fieldLabel = resources.getString(R.string.ui_homescreen_8),
            confirmLabel = resources.getString(R.string.save_task),
            dismissLabel = resources.getString(R.string.cancel_task_edit),
            confirmEnabled = editingTitle.trim().isNotEmpty(),
            enabled = !state.isSubmitting,
            errorMessage = state.errorMessageRes?.let(resources::getString),
            onConfirm = { editingTaskId?.let { id -> onUpdateTask(id, editingTitle) { editingTaskId = null } } },
            onDismiss = { editingTaskId = null },
        )
    }

    if (deletingTaskId != null) {
        AppConfirmDialog(
            title = resources.getString(R.string.confirm_delete_title),
            message = resources.getString(R.string.confirm_delete_message),
            confirmLabel = resources.getString(R.string.confirm_delete_action),
            dismissLabel = resources.getString(R.string.cancel_task_edit),
            enabled = !state.isSubmitting,
            destructive = true,
            onConfirm = { deletingTaskId?.let(onRemoveTask); deletingTaskId = null },
            onDismiss = { deletingTaskId = null },
        )
    }

    Scaffold { padding ->
        AppPullToRefreshBox(
            isRefreshing = tasks.itemCount > 0 && refreshState is LoadState.Loading,
            onRefresh = tasks::refresh,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(resources.getString(R.string.ui_homescreen_1), style = MaterialTheme.typography.headlineMedium)
                        Text("Compose + Navigation 3 + Room", style = MaterialTheme.typography.bodyMedium)
                        Text(resources.getString(R.string.task_count, state.taskCount), style = MaterialTheme.typography.bodySmall)
                        androidx.compose.foundation.layout.FlowRow {
                            TextButton(onClick = if (isAuthenticated) onLogout else onLogin) {
                                Text(if (isAuthenticated) resources.getString(R.string.ui_homescreen_2) else resources.getString(R.string.ui_homescreen_3))
                            }
                            TextButton(onClick = onOpenSummary) { Text(resources.getString(R.string.ui_homescreen_4)) }
                            TextButton(onClick = onOpenSettings) { Text(resources.getString(R.string.ui_homescreen_5)) }
                            TextButton(onClick = onOpenStatus) { Text(resources.getString(R.string.ui_homescreen_6)) }
                            TextButton(onClick = onOpenVideoPlayer) { Text(resources.getString(R.string.ui_homescreen_7)) }
                        }
                        if (state.errorMessageRes != null) {
                            Text(resources.getString(state.errorMessageRes), color = MaterialTheme.colorScheme.error)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft,
                                enabled = !state.isSubmitting,
                                onValueChange = { draft = it },
                                label = { Text(resources.getString(R.string.ui_homescreen_8)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    trimmedDraft?.let { title -> onAddTask(title) { draft = "" } }
                                },
                                enabled = trimmedDraft != null && !state.isSubmitting,
                            ) { Text(resources.getString(R.string.ui_homescreen_9)) }
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            label = { Text(resources.getString(R.string.search_tasks)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TaskFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = taskFilter == filter,
                                    onClick = { onTaskFilterChange(filter) },
                                    label = {
                                        Text(resources.getString(filter.labelResource()))
                                    },
                                )
                            }
                        }
                    }
                }
                    when {
                        tasks.itemCount == 0 &&
                            (state.isLoading || refreshState is LoadState.Loading) -> {
                            item {
                                AppLoadingState(resources.getString(R.string.ui_homescreen_10), modifier = Modifier.fillMaxWidth())
                            }
                        }
                        tasks.itemCount == 0 && refreshState is LoadState.Error -> {
                            item {
                                AppErrorState(
                                    message = resources.getString(R.string.task_read_failed),
                                    retryLabel = resources.getString(R.string.ui_homescreen_11),
                                    onRetry = tasks::retry,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        tasks.itemCount == 0 -> {
                            item {
                                val filtered = searchQuery.isNotBlank() || taskFilter != TaskFilter.ALL
                                AppEmptyState(
                                    title = resources.getString(
                                        if (filtered) R.string.no_matching_tasks else R.string.ui_homescreen_12,
                                    ),
                                    description = resources.getString(
                                        if (filtered) R.string.adjust_task_search else R.string.ui_homescreen_13,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        refreshState is LoadState.Error -> {
                            item {
                                AppErrorState(
                                    message = resources.getString(R.string.task_refresh_failed),
                                    retryLabel = resources.getString(R.string.ui_homescreen_14),
                                    onRetry = tasks::retry,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    items(
                        count = tasks.itemCount,
                        key = tasks.itemKey { task -> task.id },
                    ) { index ->
                        tasks[index]?.let { task ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = task.isCompleted,
                                        enabled = !state.isSubmitting,
                                        onCheckedChange = { completed ->
                                            onSetTaskCompleted(task.id, completed)
                                        },
                                    )
                                    Text(
                                        text = task.title,
                                        modifier = Modifier.weight(1f),
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        enabled = !state.isSubmitting,
                                        onClick = {
                                            editingTaskId = task.id
                                            editingTitle = task.title
                                        },
                                    ) { Text(resources.getString(R.string.edit_task)) }
                                    TextButton(
                                        enabled = !state.isSubmitting,
                                        onClick = { deletingTaskId = task.id },
                                    ) { Text(resources.getString(R.string.ui_homescreen_15)) }
                                }
                            }
                        }
                        }
                    }
                    when (val appendState = tasks.loadState.append) {
                        is LoadState.Loading -> item {
                            AppLoadingState(resources.getString(R.string.ui_homescreen_16), modifier = Modifier.fillMaxWidth())
                        }
                        is LoadState.Error -> item {
                            AppErrorState(
                                message = resources.getString(R.string.task_more_failed),
                                retryLabel = resources.getString(R.string.ui_homescreen_17),
                                onRetry = tasks::retry,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is LoadState.NotLoading -> Unit
                    }
            }
        }
    }
}

private fun TaskFilter.labelResource(): Int = when (this) {
    TaskFilter.ALL -> R.string.task_filter_all
    TaskFilter.ACTIVE -> R.string.task_filter_active
    TaskFilter.COMPLETED -> R.string.task_filter_completed
}

