@file:Suppress("MissingPermission")

package com.will.mvvmbase.feature.connectivity

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/** A foreground connection session. All state and native handle changes are confined to main. */
internal class BluetoothController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val update: ((WirelessState) -> WirelessState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var socket: BluetoothSocket? = null
    private var generation = 0
    private var closed = false
    private val stopScanTask = Runnable { stopScan() }
    private val timeout = Runnable {
        disconnect()
        update { it.copy(connectionMessage = R.string.connection_timeout) }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (closed) return
            runCatching {
                @Suppress("DEPRECATION")
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> if (device != null) add(device,
                        intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt())
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (device != null) {
                        add(device, null)
                        update { it.copy(message = when (device.bondState) {
                            BluetoothDevice.BOND_BONDED -> R.string.pair_success
                            BluetoothDevice.BOND_BONDING -> R.string.pairing
                            else -> R.string.pair_failed
                        }) }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (scanCallback == null) update { it.copy(scanningBluetooth = false) }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> if (adapter?.isEnabled != true) {
                        stopScan()
                        disconnect()
                    }
                }
            }.onFailure { update { it.copy(message = R.string.bluetooth_failed) } }
        }
    }

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun ready(): Boolean {
        if (adapter == null) { update { it.copy(message = R.string.bluetooth_unsupported) }; return false }
        if (!adapter.isEnabled) { update { it.copy(message = R.string.bluetooth_off) }; return false }
        return true
    }

    private fun add(device: BluetoothDevice, rssi: Int?) {
        val peer = BluetoothPeer(device.address, device.name.orEmpty(), device.bondState == BluetoothDevice.BOND_BONDED, rssi)
        update { state -> state.copy(bluetooth = (state.bluetooth.filterNot { it.address == peer.address } + peer)
            .sortedWith(compareByDescending<BluetoothPeer> { it.bonded }.thenBy { it.name })) }
    }

    fun scan(ble: Boolean) {
        if (!ready()) return
        stopScan()
        update { it.copy(bluetooth = emptyList(), message = null) }
        adapter!!.bondedDevices.forEach { add(it, null) }
        if (ble) {
            val next = adapter.bluetoothLeScanner
            if (next == null) { update { it.copy(message = R.string.bluetooth_unsupported) }; return }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handler.post { if (!closed && scanCallback === this) runCatching { add(result.device, result.rssi) } }
                }
                override fun onScanFailed(errorCode: Int) {
                    handler.post {
                        if (scanCallback === this) {
                            stopScan()
                            update { it.copy(message = R.string.scan_failed) }
                        }
                    }
                }
            }
            scanner = next
            scanCallback = callback
            next.startScan(callback)
        } else if (!adapter.startDiscovery()) {
            update { it.copy(message = R.string.scan_failed) }
            return
        }
        update { it.copy(scanningBluetooth = true) }
        handler.postDelayed(stopScanTask, 15_000)
    }

    fun stopScan() {
        handler.removeCallbacks(stopScanTask)
        val callback = scanCallback
        scanCallback = null
        runCatching { if (callback != null) scanner?.stopScan(callback) }
        scanner = null
        runCatching { adapter?.cancelDiscovery() }
        update { it.copy(scanningBluetooth = false) }
    }

    fun pair(address: String) {
        if (!ready()) return
        stopScan()
        val device = adapter!!.getRemoteDevice(address)
        update { it.copy(message = if (device.bondState == BluetoothDevice.BOND_BONDED) R.string.pair_success
            else if (device.createBond()) R.string.pairing else R.string.pair_failed) }
    }

    fun connect(address: String, ble: Boolean, serviceUuid: String) {
        if (!ready()) return
        val uuid = if (ble) null else try { UUID.fromString(serviceUuid) } catch (_: IllegalArgumentException) {
            update { it.copy(message = R.string.invalid_uuid) }; return
        }
        stopScan()
        disconnect()
        val token = generation
        update { it.copy(connectionMessage = R.string.connecting, connectedAddress = address, message = null) }
        handler.postDelayed(timeout, 20_000)
        val device = adapter!!.getRemoteDevice(address)
        if (ble) {
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) {
                    handler.post {
                        if (closed || token != generation || gatt !== client) return@post
                        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                            update { it.copy(connectionMessage = R.string.discovering_services) }
                            if (!client.discoverServices()) fail()
                        } else if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                            fail()
                        }
                    }
                }
                override fun onServicesDiscovered(client: BluetoothGatt, status: Int) {
                    handler.post {
                        if (closed || token != generation || gatt !== client) return@post
                        if (status != BluetoothGatt.GATT_SUCCESS) { fail(); return@post }
                        handler.removeCallbacks(timeout)
                        update { it.copy(connectionMessage = R.string.ble_connected,
                            services = client.services.map { service -> service.uuid.toString() }) }
                    }
                }
            }
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) fail()
        } else {
            val next = device.createRfcommSocketToServiceRecord(uuid!!)
            socket = next
            scope.launch(Dispatchers.IO) {
                try {
                    next.connect()
                    handler.post {
                        if (!closed && token == generation && socket === next) {
                            handler.removeCallbacks(timeout)
                            update { it.copy(connectionMessage = R.string.classic_connected) }
                        }
                    }
                    // Observe remote closure without buffering unbounded serial data.
                    val buffer = ByteArray(1024)
                    while (next.inputStream.read(buffer) >= 0) { /* Protocol parsing belongs to the device integration. */ }
                } catch (_: Exception) { /* Report below only if this session still owns the socket. */ }
                finally {
                    runCatching { next.close() }
                    handler.post { if (!closed && token == generation && socket === next) fail() }
                }
            }
        }
    }

    private fun fail() {
        disconnect()
        update { it.copy(connectionMessage = R.string.connection_failed) }
    }

    fun disconnect() {
        generation++
        handler.removeCallbacks(timeout)
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        runCatching { socket?.close() }
        socket = null
        update { it.copy(connectionMessage = R.string.disconnected, connectedAddress = null, services = emptyList()) }
    }

    fun close() {
        closed = true
        stopScan()
        disconnect()
        context.unregisterReceiver(receiver)
        handler.removeCallbacksAndMessages(null)
    }
}
