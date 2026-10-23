package com.will.mvvmbase.feature.feed

import com.will.mvvmbase.feature.feed.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.will.mvvmbase.core.designsystem.components.*

@Composable
internal fun FeedScreen(onBack: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val vm: FeedViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val more by vm.hasMore.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTitle by rememberSaveable { mutableStateOf("") }
    var offline by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(offline) { vm.offline(offline) }
    if (editingId != null) AppInputDialog(
        title = resources.getString(R.string.edit_item),
        value = editingTitle,
        onValueChange = { editingTitle = it },
        fieldLabel = resources.getString(R.string.title),
        confirmLabel = resources.getString(R.string.save),
        dismissLabel = resources.getString(R.string.cancel),
        confirmEnabled = editingTitle.isNotBlank(),
        enabled = !busy,
        errorMessage = if (error) resources.getString(R.string.feed_action_failed) else null,
        onConfirm = { editingId?.let { id -> vm.edit(id, editingTitle) { editingId = null } } },
        onDismiss = { editingId = null },
    )
    Scaffold(topBar = { AppTopBar(resources.getString(R.string.ui_feedscreen_1), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (vm.isDemo) resources.getString(R.string.ui_feedscreen_2) else resources.getString(R.string.ui_feedscreen_3))
                if (vm.isDemo) Row { Switch(offline, { offline = it }); Text(resources.getString(R.string.ui_feedscreen_4)) }
                AppFormField(draft, { draft = it }, resources.getString(R.string.ui_feedscreen_5), Modifier.fillMaxWidth())
                AppSubmitButton(resources.getString(R.string.ui_feedscreen_6), busy, { vm.add(draft) { draft = "" } }, enabled = draft.isNotBlank())
                FlowRow {
                    TextButton(enabled = !busy, onClick = vm::refresh) { Text(resources.getString(R.string.ui_feedscreen_7)) }
                    TextButton(enabled = !busy, onClick = vm::sync) { Text(resources.getString(R.string.ui_feedscreen_8)) }
                }
                if (error) Text(resources.getString(R.string.feed_action_failed), color = MaterialTheme.colorScheme.error)
                Text(resources.getString(R.string.ui_feedscreen_9))
            }
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(resources.getString(R.string.version_state, item.version, resources.getString(if (item.conflict) R.string.conflict else if (item.pending) R.string.pending else R.string.synced)))
                        if (item.conflict) FlowRow {
                            TextButton(enabled = !busy, onClick = { vm.resolve(item.id, true) }) { Text(resources.getString(R.string.ui_feedscreen_10)) }
                            TextButton(enabled = !busy, onClick = { vm.resolve(item.id, false) }) { Text(resources.getString(R.string.ui_feedscreen_11)) }
                        } else TextButton(enabled = !busy, onClick = { editingId = item.id; editingTitle = item.title }) { Text(resources.getString(R.string.ui_feedscreen_13)) }
                        if (vm.isDemo && !item.pending) TextButton(enabled = !busy,
                            onClick = { vm.conflict(item.id) }) { Text(resources.getString(R.string.simulate_conflict)) }
                    }
                }
            }
            item {
                if (busy) AppLoadingState()
                else if (more) TextButton(onClick = vm::more) { Text(resources.getString(R.string.ui_feedscreen_14)) }
                else Text(resources.getString(R.string.ui_feedscreen_15))
            }
        }
    }
}
