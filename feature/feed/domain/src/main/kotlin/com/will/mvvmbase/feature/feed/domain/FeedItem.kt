package com.will.mvvmbase.feature.feed.domain

data class FeedItem(val id: String, val title: String, val version: Long,
    val pending: Boolean = false, val conflict: Boolean = false)
