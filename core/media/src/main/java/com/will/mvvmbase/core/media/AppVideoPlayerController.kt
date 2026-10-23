package com.will.mvvmbase.core.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.MainThread
import com.will.mvvmbase.core.media.internal.FfmpegEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread control API backed exclusively by the project's native FFmpeg engine. */
class AppVideoPlayerController internal constructor(
    context: Context,
    private val seekBackIncrementMs: Long,
    private val seekForwardIncrementMs: Long,
) {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutableState = MutableStateFlow(AppVideoPlayerState())
    val state: StateFlow<AppVideoPlayerState> = mutableState.asStateFlow()
    private var released = false
    private var source: Uri? = null
    private var sourceSession: FfmpegSourceSession? = null
    private var resumeOnFocusGain = false
    private var ownsAudioFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        mainHandler.post {
            if (!released) when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocusGain = false; pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeOnFocusGain = state.value.isPlayWhenReady
                    pause(abandonFocus = false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine.setVolume(.2f, .2f)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    engine.setVolume(1f, 1f)
                    if (resumeOnFocusGain) { resumeOnFocusGain = false; play() }
                }
            }
        }
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private val engine: FfmpegEngine = FfmpegEngine(this.context, object : FfmpegEngine.Listener {
        override fun onPrepared(width: Int, height: Int, durationMs: Long) {
            if (released) return
            mutableState.value = state.value.copy(
                playbackState = AppVideoPlaybackState.Ready,
                durationMs = durationMs,
                isSeekable = durationMs > 0,
                videoWidth = width,
                videoHeight = height,
            )
            if (state.value.positionMs > 0) engine.seekTo(state.value.positionMs)
            if (state.value.isPlayWhenReady) play()
        }
        override fun onRenderingStart() { updateState() }
        override fun onSeekComplete() { updateState() }
        override fun onCompletion() {
            if (released) return
            mutableState.value = state.value.copy(
                playbackState = AppVideoPlaybackState.Ended,
                isPlaying = false,
                isPlayWhenReady = false,
                positionMs = state.value.durationMs,
            )
            abandonAudioFocus()
        }
        override fun onError(code: Int, message: String) {
            if (released) return
            engine.pause()
            mutableState.value = state.value.copy(
                isPlaying = false,
                error = IllegalStateException("FFmpeg playback failed ($code): $message"),
            )
            abandonAudioFocus()
        }
    })

    init {
        this.context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    @MainThread
    fun play() {
        if (released || source == null) return
        if (state.value.playbackState == AppVideoPlaybackState.Ended) {
            setMediaSource(requireNotNull(source), true, 0)
            return
        }
        mutableState.value = state.value.copy(isPlayWhenReady = true)
        if (state.value.playbackState != AppVideoPlaybackState.Ready || state.value.error != null) return
        @Suppress("DEPRECATION")
        val focus = audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        ownsAudioFocus = focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!ownsAudioFocus) {
            mutableState.value = state.value.copy(isPlayWhenReady = false, isPlaying = false)
            return
        }
        engine.start()
        mutableState.value = state.value.copy(isPlaying = true)
    }

    @MainThread
    fun pause() = pause(abandonFocus = true)

    private fun pause(abandonFocus: Boolean) {
        if (released) return
        engine.pause()
        mutableState.value = state.value.copy(isPlaying = false, isPlayWhenReady = false)
        if (abandonFocus) { resumeOnFocusGain = false; abandonAudioFocus() }
    }

    @MainThread
    fun togglePlayback() { if (state.value.isPlayWhenReady) pause() else play() }

    @MainThread
    fun seekTo(positionMs: Long) {
        if (released) return
        val target = positionMs.coerceAtLeast(0).let {
            if (state.value.durationMs > 0) it.coerceAtMost(state.value.durationMs) else it
        }
        mutableState.value = state.value.copy(positionMs = target)
        if (state.value.playbackState == AppVideoPlaybackState.Ended) {
            source?.let { setMediaSource(it, false, target) }
        } else if (state.value.playbackState == AppVideoPlaybackState.Ready) engine.seekTo(target)
    }

    @MainThread
    fun seekBy(offsetMs: Long) {
        val current = state.value.positionMs
        val target = runCatching { Math.addExact(current, offsetMs) }
            .getOrElse { if (offsetMs > 0) Long.MAX_VALUE else 0 }
        seekTo(target)
    }

    @MainThread fun seekBack() = seekBy(-seekBackIncrementMs)
    @MainThread fun seekForward() = seekBy(seekForwardIncrementMs)

    @MainThread
    fun setPlaybackSpeed(speed: Float) {
        require(speed.isFinite() && speed in .25f..4f) { "Playback speed must be between 0.25 and 4." }
        if (released) return
        engine.setSpeed(speed)
        mutableState.value = state.value.copy(playbackSpeed = speed)
    }

    @MainThread
    fun setRepeatMode(mode: AppVideoRepeatMode) {
        if (released) return
        engine.setLooping(mode != AppVideoRepeatMode.Off)
        mutableState.value = state.value.copy(repeatMode = mode)
    }

    @MainThread
    fun retry() {
        if (released) return
        source?.let { setMediaSource(it, state.value.isPlayWhenReady, state.value.positionMs) }
    }

    internal fun setMediaSource(uri: Uri, autoPlay: Boolean, positionMs: Long = 0) {
        if (released) return
        engine.release()
        sourceSession?.close()
        source = uri
        sourceSession = FfmpegSourceSession()
        mutableState.value = state.value.copy(
            playbackState = AppVideoPlaybackState.Buffering,
            isPlaying = false,
            isPlayWhenReady = autoPlay,
            isSeekable = false,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = 0,
            videoWidth = 0,
            videoHeight = 0,
            error = null,
        )
        engine.prepare(uri.toString(), state.value.repeatMode != AppVideoRepeatMode.Off, sourceSession)
    }

    internal fun setSurface(surface: Surface?) { if (!released) engine.setSurface(surface) else surface?.release() }

    internal fun updateState() {
        if (released || state.value.playbackState != AppVideoPlaybackState.Ready || state.value.error != null) return
        mutableState.value = state.value.copy(positionMs = engine.currentPosition.coerceAtLeast(0))
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        if (ownsAudioFocus) audioManager.abandonAudioFocus(focusListener)
        ownsAudioFocus = false
    }

    internal fun release() {
        if (released) return
        released = true
        engine.close()
        sourceSession?.close()
        sourceSession = null
        abandonAudioFocus()
        context.unregisterReceiver(noisyReceiver)
        mainHandler.removeCallbacksAndMessages(null)
        mutableState.value = state.value.copy(isPlaying = false, isPlayWhenReady = false)
    }
}
