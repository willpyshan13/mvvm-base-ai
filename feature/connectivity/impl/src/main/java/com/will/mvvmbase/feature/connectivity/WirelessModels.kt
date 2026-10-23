package com.will.mvvmbase.feature.connectivity

internal data class BluetoothPeer(val address: String, val name: String, val bonded: Boolean, val rssi: Int? = null)
internal data class WifiPeer(val ssid: String, val bssid: String, val level: Int, val security: WifiSecurity)
internal enum class WifiSecurity { Open, Wpa2, Wpa3, Unsupported }
internal data class WirelessState(
    val bluetooth: List<BluetoothPeer> = emptyList(),
    val wifi: List<WifiPeer> = emptyList(),
    val scanningBluetooth: Boolean = false,
    val scanningWifi: Boolean = false,
    val connectionMessage: Int = R.string.disconnected,
    val connectedAddress: String? = null,
    val services: List<String> = emptyList(),
    val wifiMessage: Int = R.string.wifi_idle,
    val currentWifi: String = "",
    val message: Int? = null,
)
