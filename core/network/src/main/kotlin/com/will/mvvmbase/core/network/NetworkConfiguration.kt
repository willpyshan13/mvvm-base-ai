package com.will.mvvmbase.core.network

data class NetworkConfiguration(val baseUrl: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}
