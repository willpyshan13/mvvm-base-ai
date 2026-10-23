package com.will.mvvmbase.core.media

import kotlinx.coroutines.flow.asStateFlow

object AppPictureInPicture {
    private val mutableFullscreen = kotlinx.coroutines.flow.MutableStateFlow(false)
    val fullscreen = mutableFullscreen.asStateFlow()
    fun fullscreen(value: Boolean) { mutableFullscreen.value = value }
    private val mutableActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    fun update(active: Boolean) { mutableActive.value = active }
}
