package com.will.mvvmbase.core.common.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observes validated internet access, including Wi-Fi connected without internet. */
fun Context.observeInternetConnectivity() = callbackFlow {
    val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
    fun publish() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
    }
    manager.registerNetworkCallback(NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
    publish()
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
