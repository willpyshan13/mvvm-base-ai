package com.will.mvvmbase.core.media

data class AppVideoPlayerState(
    val playbackState: AppVideoPlaybackState = AppVideoPlaybackState.Idle,
    val isPlaying: Boolean = false,
    val isPlayWhenReady: Boolean = false,
    val isSeekable: Boolean = false,
    val positionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val repeatMode: AppVideoRepeatMode = AppVideoRepeatMode.Off,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val error: Throwable? = null,
)
