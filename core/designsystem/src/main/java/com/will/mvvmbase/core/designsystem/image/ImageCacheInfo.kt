package com.will.mvvmbase.core.designsystem.image

data class ImageCacheInfo(
    val memoryBytes: Long,
    val memoryMaximumBytes: Long,
    val diskBytes: Long,
    val diskMaximumBytes: Long,
)
