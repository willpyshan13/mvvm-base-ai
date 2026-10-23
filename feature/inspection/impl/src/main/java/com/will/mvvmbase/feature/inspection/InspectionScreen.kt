package com.will.mvvmbase.feature.inspection

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.will.mvvmbase.core.designsystem.components.AppEmptyState
import com.will.mvvmbase.core.designsystem.components.AppErrorState
import com.will.mvvmbase.core.designsystem.components.AppLoadingState
import com.will.mvvmbase.core.designsystem.components.AppNetworkImage
import com.will.mvvmbase.core.designsystem.components.AppTopBar
import com.will.mvvmbase.core.designsystem.components.AppConfirmDialog
import com.will.mvvmbase.feature.inspection.domain.InspectionDraft
import com.will.mvvmbase.feature.inspection.domain.InspectionRecord
import java.text.DateFormat
import java.util.Date

@Composable
internal fun InspectionListRoute(onOpen: (Long?) -> Unit) {
    val viewModel: InspectionViewModel = hiltViewModel()
    val state by viewModel.records.collectAsStateWithLifecycle()
    InspectionListScreen(state, onOpen)
}

@Composable
private fun InspectionListScreen(state: InspectionListState, onOpen: (Long?) -> Unit) {
    val resources = LocalResources.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { AppTopBar(resources.getString(R.string.inspection_title)) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(resources.getString(R.string.inspection_subtitle), style = MaterialTheme.typography.titleLarge)
                    Text(resources.getString(R.string.inspection_local_notice), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onOpen(null) }) { Text(resources.getString(R.string.inspection_new)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.inspection_all, R.string.inspection_draft, R.string.inspection_done)
                            .forEachIndexed { index, label ->
                                FilterChip(selected = filter == index, onClick = { filter = index },
                                    label = { Text(resources.getString(label)) })
                            }
                    }
                }
            }
            when (state) {
                InspectionListState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    AppLoadingState(resources.getString(R.string.inspection_loading))
                }
                InspectionListState.Failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                    AppErrorState(resources.getString(R.string.inspection_load_error))
                }
                is InspectionListState.Ready -> {
                    val visible = state.records.filter { filter == 0 || it.isCompleted == (filter == 2) }
                    if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        AppEmptyState(resources.getString(R.string.inspection_empty),
                            resources.getString(R.string.inspection_empty_hint))
                    }
                    items(visible, key = InspectionRecord::id) { record ->
                        Card(onClick = { onOpen(record.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(record.equipment, style = MaterialTheme.typography.titleMedium)
                                Text(record.location, style = MaterialTheme.typography.bodyMedium)
                                Text(resources.getString(if (record.isCompleted) R.string.inspection_done else R.string.inspection_draft),
                                    color = MaterialTheme.colorScheme.primary)
                                Text(resources.getString(R.string.inspection_count, record.checkedCount),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(record.updatedAt)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InspectionEditorRoute(id: Long?, onBack: () -> Unit) {
    val viewModel: InspectionViewModel = hiltViewModel()
    val recordFlow = remember(id) { id?.let(viewModel::observe) }
    val loaded by recordFlow?.collectAsStateWithLifecycle(initialValue = InspectionLoadState.Loading)
        ?: remember { mutableStateOf<InspectionLoadState?>(null) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    var initialized by rememberSaveable(id) { mutableStateOf(id == null) }
    var equipment by rememberSaveable(id) { mutableStateOf("") }
    var location by rememberSaveable(id) { mutableStateOf("") }
    var notes by rememberSaveable(id) { mutableStateOf("") }
    var photoUri by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var checkedMask by rememberSaveable(id) { mutableIntStateOf(0) }
    var showDelete by rememberSaveable(id) { mutableStateOf(false) }
    val record = (loaded as? InspectionLoadState.Ready)?.record
    LaunchedEffect(record?.id) {
        if (!initialized && record != null) {
            equipment = record.equipment
            location = record.location
            notes = record.notes
            photoUri = record.photoUri
            checkedMask = record.checkedMask
            initialized = true
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (granted) photoUri = uri.toString() else viewModel.showError()
        }
    }
    val readOnly = record?.isCompleted == true
    if (showDelete && id != null) {
        AppConfirmDialog(
            title = resources.getString(R.string.inspection_delete_title),
            message = resources.getString(R.string.inspection_delete_body),
            confirmLabel = resources.getString(R.string.inspection_delete),
            dismissLabel = resources.getString(R.string.inspection_cancel),
            enabled = !busy,
            destructive = true,
            onConfirm = { viewModel.delete(id, onBack) },
            onDismiss = { showDelete = false },
        )
    }
    Scaffold(topBar = { AppTopBar(resources.getString(
        if (id == null) R.string.inspection_new else R.string.inspection_title), onBack) }) { padding ->
        when {
            id != null && (loaded is InspectionLoadState.Loading || !initialized && loaded is InspectionLoadState.Ready) ->
                AppLoadingState(resources.getString(R.string.inspection_loading), Modifier.padding(padding))
            id != null && loaded is InspectionLoadState.Missing ->
                AppErrorState(resources.getString(R.string.inspection_missing), modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(resources.getString(if (readOnly) R.string.inspection_completed_hint
                            else R.string.inspection_finish_hint), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(equipment, { equipment = it.take(80) },
                            label = { Text(resources.getString(R.string.inspection_equipment)) },
                            enabled = !readOnly && !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(location, { location = it.take(120) },
                            label = { Text(resources.getString(R.string.inspection_location)) },
                            enabled = !readOnly && !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(notes, { notes = it.take(2000) },
                            label = { Text(resources.getString(R.string.inspection_notes)) },
                            enabled = !readOnly && !busy, minLines = 3, modifier = Modifier.fillMaxWidth())
                        listOf(R.string.inspection_check_appearance, R.string.inspection_check_operation,
                            R.string.inspection_check_safety).forEachIndexed { index, label ->
                            val bit = 1 shl index
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checkedMask and bit != 0, enabled = !readOnly && !busy,
                                    onCheckedChange = { checked -> checkedMask =
                                        if (checked) checkedMask or bit else checkedMask and bit.inv() })
                                Text(resources.getString(label))
                            }
                        }
                        Text(resources.getString(R.string.inspection_count, Integer.bitCount(checkedMask)))
                        Text(resources.getString(R.string.inspection_photo), style = MaterialTheme.typography.titleMedium)
                        photoUri?.let { uri ->
                            AppNetworkImage(uri, resources.getString(R.string.inspection_photo),
                                Modifier.fillMaxWidth().height(180.dp), cacheEnabled = false)
                        }
                        if (!readOnly) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("image/*")) }) {
                                    Text(resources.getString(R.string.inspection_pick_photo))
                                }
                                if (photoUri != null) TextButton(enabled = !busy, onClick = { photoUri = null }) {
                                    Text(resources.getString(R.string.inspection_remove_photo))
                                }
                            }
                        }
                        if (error) Text(resources.getString(R.string.inspection_error),
                            color = MaterialTheme.colorScheme.error)
                        if (!readOnly) {
                            val draft = InspectionDraft(equipment, location, notes, photoUri, checkedMask)
                            val valid = equipment.isNotBlank() && location.isNotBlank()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = valid && !busy, onClick = {
                                    viewModel.save(id, draft, false, onBack)
                                }) { Text(resources.getString(R.string.inspection_save)) }
                                Button(enabled = valid && checkedMask == InspectionRecord.ALL_CHECKS && !busy,
                                    onClick = { viewModel.save(id, draft, true, onBack) }) {
                                    Text(resources.getString(R.string.inspection_complete))
                                }
                            }
                        }
                        if (id != null) TextButton(enabled = !busy, onClick = { showDelete = true }) {
                            Text(resources.getString(R.string.inspection_delete))
                        }
                    }
                }
            }
        }
    }
}
