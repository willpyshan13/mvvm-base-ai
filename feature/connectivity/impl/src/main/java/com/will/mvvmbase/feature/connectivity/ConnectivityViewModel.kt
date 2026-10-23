package com.will.mvvmbase.feature.connectivity

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class ConnectivityViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(WirelessState())
    val state = mutableState.asStateFlow()
    private val bluetooth = BluetoothController(application, viewModelScope) { transform -> mutableState.update(transform) }
    private val wifi = WifiController(application) { transform -> mutableState.update(transform) }

    fun permissions(wifi: Boolean): Array<String> = if (wifi) buildList {
        // Scan results still require precise location on Android 13+.
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray() else if (Build.VERSION.SDK_INT >= 31) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
    ) else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    fun granted(wifi: Boolean) = permissions(wifi).all {
        ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED
    }
    fun message(id: Int) { mutableState.update { it.copy(message = id) } }
    private fun guarded(onFailure: () -> Unit = {}, action: () -> Unit) {
        try { action() }
        catch (_: SecurityException) { onFailure(); stopScans(); message(R.string.permission_required) }
        catch (_: Exception) { onFailure(); message(R.string.operation_failed) }
    }
    fun scanBluetooth(ble: Boolean) = guarded { bluetooth.scan(ble) }
    fun pair(address: String) = guarded { bluetooth.pair(address) }
    fun connectBluetooth(address: String, ble: Boolean, uuid: String) = guarded(onFailure = bluetooth::disconnect) { bluetooth.connect(address, ble, uuid) }
    fun disconnectBluetooth() = bluetooth.disconnect()
    fun scanWifi() = guarded { wifi.scan() }
    fun connectWifi(peer: WifiPeer, password: String) = guarded { wifi.connect(peer, password) }
    fun saveWifiIntent(peer: WifiPeer, password: String): Intent? = try { wifi.saveIntent(peer, password) }
        catch (_: Exception) { message(R.string.operation_failed); null }
    fun wifiSaveResult(code: Int, data: Intent?) = wifi.onSaveResult(code, data)
    fun releaseWifi() { wifi.releaseRequest(); mutableState.update { it.copy(wifiMessage = R.string.wifi_request_released) } }
    fun refresh() { if (granted(true)) guarded { wifi.refreshCurrent() } }
    fun stopScans() { bluetooth.stopScan(); wifi.stopScan() }
    override fun onCleared() { bluetooth.close(); wifi.close() }
}
