package com.will.mvvmbase.core.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun rememberAppVideoPlayerController(
    seekBackIncrementMs: Long = 10_000,
    seekForwardIncrementMs: Long = 10_000,
): AppVideoPlayerController {
    require(seekBackIncrementMs > 0 && seekForwardIncrementMs > 0)
    val context = LocalContext.current.applicationContext
    val controller = remember(context, seekBackIncrementMs, seekForwardIncrementMs) {
        AppVideoPlayerController(context, seekBackIncrementMs, seekForwardIncrementMs)
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/** Native FFmpeg demux/decoding, ANativeWindow video, AudioTrack audio and Compose controls. */
@Composable
fun AppVideoPlayer(
    mediaUri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    repeat: Boolean = false,
    onPlaybackError: ((Throwable) -> Unit)? = null,
    controller: AppVideoPlayerController = rememberAppVideoPlayerController(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findPlayerActivity()
    var savedPosition by rememberSaveable(mediaUri.toString()) { mutableLongStateOf(0L) }
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val state by controller.state.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(controller, mediaUri) {
        val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        controller.setMediaSource(mediaUri, autoPlay && resumed, savedPosition)
    }
    LaunchedEffect(controller, repeat) {
        controller.setRepeatMode(if (repeat) AppVideoRepeatMode.One else AppVideoRepeatMode.Off)
    }
    LaunchedEffect(state.error) { state.error?.let { currentOnPlaybackError?.invoke(it) } }
    LaunchedEffect(controller, mediaUri) {
        while (true) {
            controller.updateState()
            savedPosition = controller.state.value.positionMs
            delay(250)
        }
    }
    DisposableEffect(controller, lifecycleOwner, autoPlay) {
        var resumeAfterPause = controller.state.value.isPlayWhenReady || autoPlay
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    resumeAfterPause = controller.state.value.isPlayWhenReady
                    savedPosition = controller.state.value.positionMs
                    if (android.os.Build.VERSION.SDK_INT < 26 || activity?.isInPictureInPictureMode != true) controller.pause()
                }
                Lifecycle.Event.ON_STOP -> controller.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (resumeAfterPause) { controller.play(); resumeAfterPause = false }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savedPosition = controller.state.value.positionMs
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            controller.setSurface(Surface(texture))
                            fitVideo(this@apply, currentState.videoWidth, currentState.videoHeight)
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                            fitVideo(this@apply, currentState.videoWidth, currentState.videoHeight)
                        }
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            controller.setSurface(null)
                            return true
                        }
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            update = { fitVideo(it, state.videoWidth, state.videoHeight) },
            onRelease = { controller.setSurface(null) },
        )
        if (state.playbackState == AppVideoPlaybackState.Buffering && state.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        Row(Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = .6f))) {
            if (state.error != null) {
                TextButton(onClick = controller::retry) { Text(stringResource(R.string.player_retry), color = Color.White) }
            } else {
                TextButton(onClick = controller::togglePlayback) {
                    Text(stringResource(if (state.isPlayWhenReady) R.string.player_pause else R.string.player_play), color = Color.White)
                }
            }
        }
    }
}

private fun fitVideo(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (view.width == 0 || view.height == 0 || videoWidth == 0 || videoHeight == 0) return
    val viewRatio = view.width.toFloat() / view.height
    val videoRatio = videoWidth.toFloat() / videoHeight
    val matrix = Matrix()
    matrix.setScale(
        if (videoRatio < viewRatio) videoRatio / viewRatio else 1f,
        if (videoRatio > viewRatio) viewRatio / videoRatio else 1f,
        view.width / 2f, view.height / 2f,
    )
    view.setTransform(matrix)
}

internal tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

