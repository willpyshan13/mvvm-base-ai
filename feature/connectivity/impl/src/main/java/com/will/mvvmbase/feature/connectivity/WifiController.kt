@file:Suppress("DEPRECATION", "MissingPermission")

package com.will.mvvmbase.feature.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.location.LocationManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

internal class WifiController(
    private val context: Context,
    private val update: ((WirelessState) -> WirelessState) -> Unit,
) {
    private val manager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var closed = false
    private var legacyTarget: String? = null
    private val connectionTimeout = Runnable {
        legacyTarget = null
        update { it.copy(wifiMessage = R.string.wifi_connection_failed) }
    }
    private val scanTimeout = Runnable {
        update { it.copy(scanningWifi = false, wifiMessage = R.string.wifi_scan_timeout) }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (closed) return
            runCatching {
                when (intent?.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        handler.removeCallbacks(scanTimeout)
                        update { it.copy(scanningWifi = false, wifiMessage = if (
                            intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) R.string.wifi_scan_done
                            else R.string.wifi_scan_cached) }
                        readResults()
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION, WifiManager.WIFI_STATE_CHANGED_ACTION -> refreshCurrent()
                }
            }.onFailure { update { it.copy(scanningWifi = false, message = R.string.wifi_failed) } }
        }
    }

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_EXPORTED)
    }

    fun refreshCurrent() {
        val info = manager?.connectionInfo
        val name = info?.ssid?.takeUnless { it == "<unknown ssid>" }?.removeSurrounding("\"").orEmpty()
        update { it.copy(currentWifi = name) }
        if (legacyTarget != null && name == legacyTarget && info?.supplicantState == SupplicantState.COMPLETED) {
            legacyTarget = null
            handler.removeCallbacks(connectionTimeout)
            update { it.copy(wifiMessage = R.string.wifi_connected) }
        }
    }

    fun scan() {
        if (manager == null) { update { it.copy(message = R.string.wifi_unsupported) }; return }
        if (!manager.isWifiEnabled) { update { it.copy(message = R.string.wifi_off) }; return }
        val location = context.getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled
            else location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!locationEnabled) { update { it.copy(message = R.string.location_required) }; return }
        update { it.copy(message = null) }
        refreshCurrent()
        readResults()
        handler.removeCallbacks(scanTimeout)
        val started = manager.startScan()
        update { it.copy(scanningWifi = started, wifiMessage = if (started) R.string.wifi_scanning else R.string.wifi_scan_cached) }
        if (started) handler.postDelayed(scanTimeout, 15_000)
    }

    private fun readResults() {
        val peers = manager?.scanResults.orEmpty().filter { it.SSID.isNotBlank() }
            .sortedByDescending { it.level }
            .map { WifiPeer(it.SSID, it.BSSID, it.level, WifiCredentials.security(it.capabilities)) }
            .distinctBy { it.ssid to it.security }
        update { it.copy(wifi = peers) }
    }

    /** Android 11+ saves the network through a system-owned approval activity. */
    fun saveIntent(peer: WifiPeer, password: String): Intent? {
        if (Build.VERSION.SDK_INT < 30) return null
        require(WifiCredentials.validPassword(peer.security, password))
        val suggestion = WifiNetworkSuggestion.Builder().setSsid(peer.ssid).apply {
            when (peer.security) {
                WifiSecurity.Wpa2 -> setWpa2Passphrase(password)
                WifiSecurity.Wpa3 -> setWpa3Passphrase(password)
                else -> Unit
            }
        }.build()
        return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
            Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
    }

    fun onSaveResult(resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT < 30) return
        val results = data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
        val success = resultCode == android.app.Activity.RESULT_OK && results?.singleOrNull() in listOf(
            Settings.ADD_WIFI_RESULT_SUCCESS, Settings.ADD_WIFI_RESULT_ALREADY_EXISTS)
        update { it.copy(wifiMessage = if (success) R.string.wifi_saved else R.string.wifi_save_cancelled) }
        runCatching { refreshCurrent() }
    }

    fun connect(peer: WifiPeer, password: String) {
        if (!WifiCredentials.validPassword(peer.security, password)) {
            update { it.copy(message = R.string.invalid_password) }; return
        }
        releaseRequest()
        if (Build.VERSION.SDK_INT >= 29) {
            val specifier = WifiNetworkSpecifier.Builder().setSsid(peer.ssid).apply {
                when (peer.security) {
                    WifiSecurity.Wpa2 -> setWpa2Passphrase(password)
                    WifiSecurity.Wpa3 -> setWpa3Passphrase(password)
                    else -> Unit
                }
            }.build()
            val next = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { if (!closed && callback === this) {
                        update { it.copy(wifiMessage = R.string.wifi_local_connected, currentWifi = peer.ssid) }
                    } }
                }
                override fun onUnavailable() {
                    handler.post { if (!closed && callback === this) {
                        releaseRequest()
                        update { it.copy(wifiMessage = R.string.wifi_connection_failed) }
                    } }
                }
                override fun onLost(network: Network) {
                    handler.post { if (!closed && callback === this) {
                        releaseRequest()
                        update { it.copy(wifiMessage = R.string.wifi_connection_lost, currentWifi = "") }
                    } }
                }
            }
            callback = next
            connectivity.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(specifier).build(), next, 30_000)
        } else {
            if (peer.security == WifiSecurity.Wpa3) {
                update { it.copy(message = R.string.security_unsupported) }; return
            }
            val config = WifiConfiguration().apply {
                SSID = quote(peer.ssid)
                if (peer.security == WifiSecurity.Open) allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                else { allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK); preSharedKey = quote(password) }
            }
            val existing = manager?.configuredNetworks?.firstOrNull { it.SSID == config.SSID }
            val id = if (existing == null) manager?.addNetwork(config) ?: -1 else {
                config.networkId = existing.networkId
                manager?.updateNetwork(config) ?: -1
            }
            if (id < 0 || manager?.enableNetwork(id, true) != true || manager.reconnect() != true) {
                update { it.copy(wifiMessage = R.string.wifi_connection_failed) }; return
            }
        }
        if (Build.VERSION.SDK_INT < 29) {
            legacyTarget = peer.ssid
            handler.postDelayed(connectionTimeout, 30_000)
        }
        update { it.copy(wifiMessage = R.string.wifi_connecting) }
        if (Build.VERSION.SDK_INT < 29) refreshCurrent()
    }

    fun releaseRequest() {
        legacyTarget = null
        handler.removeCallbacks(connectionTimeout)
        val previous = callback
        callback = null
        if (previous != null) runCatching { connectivity.unregisterNetworkCallback(previous) }
    }

    fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        update { it.copy(scanningWifi = false) }
    }

    fun close() {
        closed = true
        releaseRequest()
        stopScan()
        context.unregisterReceiver(receiver)
        handler.removeCallbacksAndMessages(null)
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
