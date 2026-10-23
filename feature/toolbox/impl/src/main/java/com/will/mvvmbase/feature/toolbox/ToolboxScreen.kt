package com.will.mvvmbase.feature.toolbox

import com.will.mvvmbase.feature.toolbox.R

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.will.mvvmbase.core.designsystem.components.*
import com.will.mvvmbase.core.navigation.Navigator
import com.will.mvvmbase.core.network.NetworkDiagnostics
import com.will.mvvmbase.core.platform.*
import com.will.mvvmbase.feature.media.VideoPlayerKey
import com.will.mvvmbase.feature.status.StatusKey
import java.io.File
import com.will.mvvmbase.core.designsystem.image.AppImages
import com.will.mvvmbase.core.designsystem.image.ImageCacheInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun ToolboxScreen(navigator: Navigator) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageCacheInfo by remember { mutableStateOf<ImageCacheInfo?>(null) }
    var clearingImages by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        while (true) {
            imageCacheInfo = AppImages.inspect(context)
            delay(5_000)
        }
    }
    var cacheInfo by remember { mutableStateOf<com.will.mvvmbase.core.media.VideoCacheInfo?>(null) }
    LaunchedEffect(Unit) { cacheInfo = withContext(Dispatchers.IO) { com.will.mvvmbase.core.media.AppVideoCacheSettings.inspect(context) } }
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var imageUrl by rememberSaveable { mutableStateOf("https://picsum.photos/600/300") }
    var transferUrl by rememberSaveable { mutableStateOf("") }
    var transferId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val requests by NetworkDiagnostics.requests.collectAsStateWithLifecycle()
    val result by navigator.result(ToolboxResult).collectAsStateWithLifecycle(initialValue = null)
    val manager = remember(context) { WorkManager.getInstance(context) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedFile = it.toString()
            message = resources.getString(R.string.ui_toolboxscreen_1)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedFile = it.toString()
            imageUrl = it.toString()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) imageUrl = cameraFile.orEmpty()
        else message = resources.getString(R.string.ui_toolboxscreen_2)
    }
    fun notify() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.data = Uri.parse("wbase://open/toolbox")
            try { AppNotifications.show(context, intent); message = resources.getString(R.string.ui_toolboxscreen_3) }
            catch (_: SecurityException) { message = resources.getString(R.string.ui_toolboxscreen_21) }
        }
    }
    Scaffold(topBar = { AppTopBar(resources.getString(R.string.ui_toolboxscreen_4)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .widthIn(max = 840.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(resources.getString(R.string.ui_toolboxscreen_5), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { navigator.navigate(StatusKey) }) { Text(resources.getString(R.string.ui_toolboxscreen_6)) }
            TextButton(onClick = { navigator.navigate(VideoPlayerKey) }) { Text(resources.getString(R.string.ui_toolboxscreen_7)) }
            TextButton(onClick = { navigator.navigate(com.will.mvvmbase.feature.feed.FeedKey) }) { Text(resources.getString(R.string.ui_toolboxscreen_8)) }
            TextButton(onClick = { navigator.navigate(ResultPickerKey) }) { Text(resources.getString(R.string.ui_toolboxscreen_9)) }
            result?.let { value ->
                Text(resources.getString(R.string.result_value, value))
                TextButton(onClick = { navigator.consumeResult(ToolboxResult) }) { Text(resources.getString(R.string.ui_toolboxscreen_10)) }
            }
            TextButton(onClick = { navigator.navigate(com.will.mvvmbase.feature.connectivity.ConnectivityKey) }) { Text(resources.getString(R.string.connectivity_entry)) }
            HorizontalDivider()
            Text(resources.getString(R.string.ui_toolboxscreen_11), style = MaterialTheme.typography.titleLarge)
            AppFormField(imageUrl, { imageUrl = it }, resources.getString(R.string.ui_toolboxscreen_12), Modifier.fillMaxWidth())
            AppNetworkImage(imageUrl, resources.getString(R.string.ui_toolboxscreen_13), Modifier.fillMaxWidth().height(180.dp))
            imageCacheInfo?.let { info ->
                Text(resources.getString(R.string.image_cache_usage,
                    info.memoryBytes / 1024, info.memoryMaximumBytes / 1024,
                    info.diskBytes / 1024, info.diskMaximumBytes / 1024))
            }
            TextButton(enabled = !clearingImages, onClick = {
                scope.launch {
                    clearingImages = true
                    try {
                        AppImages.clear(context)
                        imageCacheInfo = AppImages.inspect(context)
                        message = resources.getString(R.string.image_cache_cleared)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message = resources.getString(R.string.image_cache_clear_failed)
                    } finally {
                        clearingImages = false
                    }
                }
            }) { Text(resources.getString(R.string.clear_image_cache)) }
            TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text(resources.getString(R.string.ui_toolboxscreen_14)) }
            TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text(resources.getString(R.string.ui_toolboxscreen_15)) }
            TextButton(onClick = {
                val file = File(context.cacheDir, "camera").apply { mkdirs() }
                    .let { File.createTempFile("photo-", ".jpg", it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                cameraFile = uri.toString()
                try { camera.launch(uri) } catch (_: Exception) { message = resources.getString(R.string.ui_toolboxscreen_16) }
            }) { Text(resources.getString(R.string.ui_toolboxscreen_17)) }
            selectedFile?.let { Text(resources.getString(R.string.ui_toolboxscreen_18)) }
            HorizontalDivider()
            cacheInfo?.let { Text(resources.getString(R.string.video_cache_usage, it.usedBytes / 1048576, it.maximumBytes / 1048576)) }
            TextButton(onClick = { scope.launch {
                val cleared = withContext(Dispatchers.IO) { com.will.mvvmbase.core.media.AppVideoCacheSettings.clear(context) }
                message = if (cleared) resources.getString(R.string.cache_cleared) else resources.getString(R.string.close_player_first)
                cacheInfo = withContext(Dispatchers.IO) { com.will.mvvmbase.core.media.AppVideoCacheSettings.inspect(context) }
            } }) { Text(resources.getString(R.string.clear_video_cache)) }
            HorizontalDivider()
            Text(resources.getString(R.string.ui_toolboxscreen_19), style = MaterialTheme.typography.titleLarge)
            if (Build.VERSION.SDK_INT >= 33) AppPermissionGate(Manifest.permission.POST_NOTIFICATIONS,
                resources.getString(R.string.ui_toolboxscreen_21)) {
                TextButton(onClick = ::notify) { Text(resources.getString(R.string.ui_toolboxscreen_20)) }
            } else TextButton(onClick = ::notify) { Text(resources.getString(R.string.ui_toolboxscreen_20)) }
            HorizontalDivider()
            Text(resources.getString(R.string.ui_toolboxscreen_23), style = MaterialTheme.typography.titleLarge)
            Text(resources.getString(R.string.ui_toolboxscreen_24))
            AppFormField(transferUrl, { transferUrl = it }, resources.getString(R.string.ui_toolboxscreen_25), Modifier.fillMaxWidth())
            fun start(upload: Boolean) {
                try {
                    transferId = AppTransfers.enqueue(context, transferUrl,
                        if (upload) selectedFile?.let(Uri::parse) else null).toString()
                    message = null
                } catch (_: Exception) { message = resources.getString(R.string.ui_toolboxscreen_26) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { start(false) }) { Text(resources.getString(R.string.ui_toolboxscreen_27)) }
                TextButton(enabled = selectedFile != null, onClick = { start(true) }) { Text(resources.getString(R.string.ui_toolboxscreen_28)) }
            }
            transferId?.let { value ->
                val id = remember(value) { UUID.fromString(value) }
                val work by remember(id) { manager.getWorkInfoByIdFlow(id) }.collectAsStateWithLifecycle(initialValue = null)
                Text(resources.getString(R.string.ui_toolboxscreen_29, work?.state?.name ?: "WAITING", work?.progress?.getLong("bytes", 0) ?: 0))
                work?.outputData?.getString("error")?.let { error ->
                    val message = when (error) {
                        "file_unavailable_or_too_large" -> resources.getString(R.string.transfer_file_error)
                        "retries_exhausted" -> resources.getString(R.string.transfer_retries_error)
                        else -> error
                    }
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (work?.state?.isFinished == false) TextButton(onClick = { AppTransfers.cancel(context, id) }) { Text(resources.getString(R.string.ui_toolboxscreen_30)) }
                if (work?.state == androidx.work.WorkInfo.State.SUCCEEDED) Text(resources.getString(R.string.ui_toolboxscreen_31))
            }
            message?.let { Text(it) }
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                HorizontalDivider()
                Text(resources.getString(R.string.ui_toolboxscreen_32), style = MaterialTheme.typography.titleLarge)
                Text(resources.getString(R.string.debug_app, context.packageName))
                Text(resources.getString(R.string.debug_first_frame, AppDiagnostics.startupMs ?: 0))
                Text(resources.getString(R.string.debug_last_crash,
                    AppDiagnostics.lastCrash(context, resources.getString(R.string.debug_no_crash))))
                Text(resources.getString(R.string.ui_toolboxscreen_33))
                TextButton(onClick = NetworkDiagnostics::clear) { Text(resources.getString(R.string.ui_toolboxscreen_34)) }
                requests.forEach { Text(resources.getString(R.string.debug_request,
                    it.method, it.host, it.status ?: resources.getString(R.string.debug_failed), it.durationMs)) }
            }
        }
    }
}
