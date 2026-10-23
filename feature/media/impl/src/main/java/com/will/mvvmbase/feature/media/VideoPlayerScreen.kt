package com.will.mvvmbase.feature.media

import com.will.mvvmbase.feature.media.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.will.mvvmbase.core.media.AppPictureInPicture
import com.will.mvvmbase.core.platform.AppTransfers
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.will.mvvmbase.core.common.android.observeInternetConnectivity
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.util.UUID
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import com.will.mvvmbase.core.common.android.toHttpUriOrNull
import com.will.mvvmbase.core.media.AppVideoPlayer
import com.will.mvvmbase.core.media.rememberAppVideoPlayerController
import kotlin.math.roundToLong

private const val SAMPLE_VIDEO_URL =
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"

@Composable
internal fun VideoPlayerScreen(onBack: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    var source by rememberSaveable { mutableStateOf(SAMPLE_VIDEO_URL) }
    var activeSource by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val parsedSource = remember(source) { source.toHttpUriOrNull() }

    val context = LocalContext.current
    val activity = context.playerActivity()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val pip by AppPictureInPicture.active.collectAsStateWithLifecycle()
    var downloadId by rememberSaveable { mutableStateOf<String?>(null) }
    var transferId by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadSource by rememberSaveable { mutableStateOf<String?>(null) }
    val online by remember(context) { context.observeInternetConnectivity() }
        .collectAsStateWithLifecycle(initialValue = true)
    var wasOffline by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(online) { if (!online) wasOffline = true }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            activeSource = it.toString()
        }
    }
    DisposableEffect(fullscreen, activity) {
        AppPictureInPicture.fullscreen(fullscreen)
        val oldOrientation = activity?.requestedOrientation
        if (fullscreen && activity != null) {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            AppPictureInPicture.fullscreen(false)
            if (fullscreen && activity != null) {
                activity.requestedOrientation = oldOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    androidx.activity.compose.BackHandler(fullscreen) { fullscreen = false }
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen || pip) Modifier else Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(20.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!fullscreen && !pip) {
            TextButton(onClick = onBack) { Text(resources.getString(R.string.ui_videoplayerscreen_1)) }
            Text(resources.getString(R.string.ui_videoplayerscreen_2), style = MaterialTheme.typography.headlineMedium)
            Text(
                resources.getString(R.string.ui_videoplayerscreen_3),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                resources.getString(R.string.ui_videoplayerscreen_4),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = source,
                onValueChange = {
                    source = it
                    errorMessage = null
                },
                label = { Text(resources.getString(R.string.ui_videoplayerscreen_5)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val uri = parsedSource
                    if (uri == null) {
                        errorMessage = resources.getString(R.string.ui_videoplayerscreen_6)
                    } else {
                        activeSource = uri.toString()
                        errorMessage = null
                    }
                },
                enabled = source.isNotBlank(),
            ) {
                Text(if (activeSource == null) resources.getString(R.string.ui_videoplayerscreen_7) else resources.getString(R.string.ui_videoplayerscreen_8))
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { picker.launch(arrayOf("video/*")) }) { Text(resources.getString(R.string.ui_videoplayerscreen_9)) }
            TextButton(enabled = parsedSource != null, onClick = {
                try {
                    downloadId = AppTransfers.enqueue(context, source).toString()
                    transferId = downloadId
                    downloadSource = source
                }
                catch (_: Exception) { errorMessage = resources.getString(R.string.ui_videoplayerscreen_10) }
            }) { Text(resources.getString(R.string.ui_videoplayerscreen_11)) }
            Text(resources.getString(R.string.ui_videoplayerscreen_12))
            downloadId?.let { id ->
                val info by remember(id) { WorkManager.getInstance(context).getWorkInfoByIdFlow(UUID.fromString(id)) }
                    .collectAsStateWithLifecycle(initialValue = null)
                Text(resources.getString(R.string.download_state,
                    info?.state?.name ?: resources.getString(R.string.download_waiting)))
                if (!online) Text(resources.getString(R.string.download_offline))
                else if (wasOffline && info?.state?.isFinished == false) {
                    Text(resources.getString(R.string.download_reconnected))
                }
                val bytes = info?.progress?.getLong("bytes", 0L) ?: 0L
                val total = info?.progress?.getLong("total", -1L) ?: -1L
                var stalled by remember(id) { mutableStateOf(false) }
                LaunchedEffect(id, bytes, info?.state, online) {
                    stalled = false
                    if (online && info?.state == WorkInfo.State.RUNNING) {
                        delay(8_000)
                        stalled = true
                    }
                }
                if (info?.state == WorkInfo.State.RUNNING) {
                    Text(if (total > 0) "${bytes / 1024} / ${total / 1024} KiB" else "${bytes / 1024} KiB")
                    if (online && (stalled || info?.progress?.getBoolean("weak", false) == true)) {
                        Text(resources.getString(R.string.download_weak), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (online && info?.state == WorkInfo.State.ENQUEUED && (info?.runAttemptCount ?: 0) > 0) {
                    Text(resources.getString(R.string.download_retrying))
                }
                info?.outputData?.getString("error")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (info?.state == WorkInfo.State.FAILED || info?.state == WorkInfo.State.CANCELLED) {
                    TextButton(onClick = {
                        downloadSource?.let { url ->
                            downloadId = AppTransfers.enqueue(context, url,
                                resumeId = transferId?.let(UUID::fromString)).toString()
                        }
                    }) { Text(resources.getString(R.string.download_resume)) }
                }
                if (info?.state?.isFinished == false) TextButton(onClick = { AppTransfers.cancel(context, UUID.fromString(id)) }) { Text(resources.getString(R.string.ui_videoplayerscreen_13)) }
                info?.outputData?.getString("file")?.let { path ->
                    TextButton(onClick = { activeSource = Uri.fromFile(File(path)).toString() }) { Text(resources.getString(R.string.ui_videoplayerscreen_14)) }
                }
            }
            }
            activeSource?.let { uri ->
                val controller = rememberAppVideoPlayerController()
                val playerState by controller.state.collectAsStateWithLifecycle()
                var seekFraction by remember(uri) { mutableStateOf<Float?>(null) }
                AppVideoPlayer(
                    mediaUri = Uri.parse(uri),
                    modifier = if (fullscreen || pip) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    autoPlay = true,
                    controller = controller,
                    onPlaybackError = {
                        errorMessage = resources.getString(R.string.ui_videoplayerscreen_15)
                    },
                )
                if (!pip) {
                Row {
                    TextButton(onClick = { fullscreen = !fullscreen }) { Text(if (fullscreen) resources.getString(R.string.ui_videoplayerscreen_16) else resources.getString(R.string.ui_videoplayerscreen_17)) }
                    if (android.os.Build.VERSION.SDK_INT >= 26 && activity != null &&
                        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        TextButton(onClick = {
                            try {
                                activity.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(16, 9)).build())
                            } catch (_: Exception) { errorMessage = resources.getString(R.string.ui_videoplayerscreen_18) }
                        }) { Text(resources.getString(R.string.ui_videoplayerscreen_19)) }
                    }
                }
                if (!fullscreen) {
                Text(
                    "${formatTime(playerState.positionMs)} / ${formatTime(playerState.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    resources.getString(R.string.playback_speed, playerState.playbackSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = seekFraction ?: if (playerState.durationMs > 0) {
                        playerState.positionMs.toFloat().div(playerState.durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onValueChange = { fraction -> seekFraction = fraction },
                    onValueChangeFinished = {
                        val fraction = seekFraction
                        if (fraction != null && playerState.durationMs > 0 && playerState.isSeekable) {
                            controller.seekTo((playerState.durationMs * fraction).roundToLong())
                        }
                        seekFraction = null
                    },
                    enabled = playerState.isSeekable && playerState.durationMs > 0,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = controller::seekBack) { Text(resources.getString(R.string.ui_videoplayerscreen_20)) }
                    TextButton(onClick = controller::seekForward) { Text(resources.getString(R.string.ui_videoplayerscreen_21)) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { controller.setPlaybackSpeed(1f) }) { Text("1×") }
                    TextButton(onClick = { controller.setPlaybackSpeed(1.5f) }) { Text("1.5×") }
                    TextButton(onClick = { controller.setPlaybackSpeed(2f) }) { Text("2×") }
                }
            }
                }
                }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private tailrec fun Context.playerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.playerActivity()
    else -> null
}
