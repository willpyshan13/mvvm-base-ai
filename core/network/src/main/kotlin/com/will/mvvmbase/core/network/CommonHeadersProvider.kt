package com.will.mvvmbase.core.network

/** Read for each API call. Implementations must be thread safe and must not block on IO. */
fun interface CommonHeadersProvider {
    fun headers(): Map<String, String>
}
