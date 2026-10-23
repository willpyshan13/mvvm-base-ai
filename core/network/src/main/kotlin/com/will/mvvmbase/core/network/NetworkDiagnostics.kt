package com.will.mvvmbase.core.network

import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Interceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RequestSummary(val method: String, val host: String, val status: Int?, val durationMs: Long)

object NetworkDiagnostics {
    private val mutableRequests = MutableStateFlow<List<RequestSummary>>(emptyList())
    val requests = mutableRequests.asStateFlow()
    @Synchronized fun clear() { mutableRequests.value = emptyList() }
    @Synchronized private fun record(summary: RequestSummary) {
        mutableRequests.value = (listOf(summary) + mutableRequests.value).take(50)
    }
    val interceptor = Interceptor { chain ->
        val start = System.nanoTime()
        var status: Int? = null
        try { chain.proceed(chain.request()).also { status = it.code } }
        finally { record(RequestSummary(chain.request().method, chain.request().url.host,
            status, (System.nanoTime() - start) / 1_000_000)) }
    }
}
