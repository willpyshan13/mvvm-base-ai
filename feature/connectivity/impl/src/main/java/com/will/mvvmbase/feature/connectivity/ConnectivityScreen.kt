package com.will.mvvmbase.feature.connectivity

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.will.mvvmbase.core.designsystem.components.AppTopBar

@Composable
internal fun ConnectivityRoute(onBack: () -> Unit, viewModel: ConnectivityViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWifi by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending
        pending = null
        if (viewModel.granted(pendingWifi)) action?.invoke() else viewModel.message(R.string.permission_required)
    }
    fun authorized(wifi: Boolean, action: () -> Unit) {
        if (viewModel.granted(wifi)) action() else {
            pendingWifi = wifi
            pending = action
            permissions.launch(viewModel.permissions(wifi))
        }
    }
    val activity = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }
    fun open(intent: Intent) {
        try { activity.launch(intent) } catch (_: Exception) { viewModel.message(R.string.settings_unavailable) }
    }
    val saveWifi = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.wifiSaveResult(it.resultCode, it.data)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopScans()
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.stopScans() }
    }
    ConnectivityScreen(state, onBack,
        onScanBluetooth = { ble -> authorized(false) { viewModel.scanBluetooth(ble) } },
        onStop = viewModel::stopScans,
        onPair = { address -> authorized(false) { viewModel.pair(address) } },
        onConnectBluetooth = { address, ble, uuid -> authorized(false) { viewModel.connectBluetooth(address, ble, uuid) } },
        onDisconnect = viewModel::disconnectBluetooth,
        onEnableBluetooth = { authorized(false) { open(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } },
        onBluetoothSettings = { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        onScanWifi = { authorized(true, viewModel::scanWifi) },
        onJoinWifi = { peer, password ->
            authorized(true) {
                if (Build.VERSION.SDK_INT >= 30) {
                    viewModel.saveWifiIntent(peer, password)?.let { intent ->
                        try { saveWifi.launch(intent) } catch (_: Exception) { viewModel.message(R.string.settings_unavailable) }
                    }
                } else viewModel.connectWifi(peer, password)
            }
        },
        onReleaseWifi = viewModel::releaseWifi,
        onWifiSettings = { open(Intent(Settings.ACTION_WIFI_SETTINGS)) },
        onLocationSettings = { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onAppSettings = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) },
    )
}

@Composable
private fun ConnectivityScreen(
    state: WirelessState,
    onBack: () -> Unit,
    onScanBluetooth: (Boolean) -> Unit,
    onStop: () -> Unit,
    onPair: (String) -> Unit,
    onConnectBluetooth: (String, Boolean, String) -> Unit,
    onDisconnect: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onScanWifi: () -> Unit,
    onJoinWifi: (WifiPeer, String) -> Unit,
    onReleaseWifi: () -> Unit,
    onWifiSettings: () -> Unit,
    onLocationSettings: () -> Unit,
    onAppSettings: () -> Unit,
) {
    var ble by rememberSaveable { mutableStateOf(true) }
    var uuid by rememberSaveable { mutableStateOf("00001101-0000-1000-8000-00805f9b34fb") }
    var joining by remember { mutableStateOf<WifiPeer?>(null) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.permissions_note))
            Row {
                TextButton(onClick = onAppSettings) { Text(stringResource(R.string.app_settings)) }
                TextButton(onClick = onLocationSettings) { Text(stringResource(R.string.location_settings)) }
            }
            Text(stringResource(R.string.bluetooth_title), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ble, onClick = { onStop(); ble = true }, label = { Text("BLE") })
                FilterChip(selected = !ble, onClick = { onStop(); ble = false }, label = { Text(stringResource(R.string.classic)) })
            }
            Text(stringResource(if (ble) R.string.ble_note else R.string.classic_note))
            if (!ble) OutlinedTextField(uuid, { uuid = it }, label = { Text(stringResource(R.string.service_uuid)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onScanBluetooth(ble) }, enabled = !state.scanningBluetooth) { Text(stringResource(R.string.scan)) }
                TextButton(onClick = onStop) { Text(stringResource(R.string.stop_scan)) }
                TextButton(onClick = onEnableBluetooth) { Text(stringResource(R.string.enable_bluetooth)) }
                TextButton(onClick = onBluetoothSettings) { Text(stringResource(R.string.bluetooth_settings)) }
            }
            if (state.scanningBluetooth) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(state.connectionMessage))
            state.connectedAddress?.let { Text(it); TextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) } }
            state.services.forEach { Text(stringResource(R.string.gatt_service, it), style = MaterialTheme.typography.bodySmall) }
            if (state.bluetooth.isEmpty()) Text(stringResource(R.string.no_bluetooth))
            state.bluetooth.forEach { peer ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(peer.name.ifBlank { stringResource(R.string.unnamed_device) })
                        Text(peer.address + (peer.rssi?.let { " · $it dBm" } ?: ""))
                        if (peer.bonded) Text(stringResource(R.string.paired))
                        Row {
                            TextButton(onClick = { onPair(peer.address) }) { Text(stringResource(R.string.pair)) }
                            TextButton(onClick = { onConnectBluetooth(peer.address, ble, uuid) }) {
                                Text(stringResource(if (ble) R.string.connect_ble else R.string.connect_classic))
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.wifi_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.wifi_note))
            Text(stringResource(R.string.current_wifi, state.currentWifi.ifBlank { stringResource(R.string.unknown_wifi) }))
            Text(stringResource(state.wifiMessage))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScanWifi, enabled = !state.scanningWifi) { Text(stringResource(R.string.refresh_wifi)) }
                TextButton(onClick = onWifiSettings) { Text(stringResource(R.string.wifi_settings)) }
                if (Build.VERSION.SDK_INT == 29) TextButton(onClick = onReleaseWifi) { Text(stringResource(R.string.release_wifi)) }
            }
            if (state.scanningWifi) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.wifi.isEmpty()) Text(stringResource(R.string.no_wifi))
            state.wifi.forEach { peer ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(peer.ssid)
                        Text("${peer.level} dBm · ${peer.security.name}")
                        TextButton(onClick = { if (peer.security == WifiSecurity.Unsupported) onWifiSettings() else joining = peer }) {
                            Text(stringResource(if (peer.security == WifiSecurity.Unsupported) R.string.wifi_settings else R.string.join_wifi))
                        }
                    }
                }
            }
        }
    }
    joining?.let { peer ->
        // Credentials live only in this dialog; they are never saved to state restoration or disk.
        var password by remember(peer) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { joining = null }, title = { Text(peer.ssid) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.join_note))
                if (peer.security != WifiSecurity.Open) OutlinedTextField(password, { password = it },
                    label = { Text(stringResource(R.string.password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                if (!WifiCredentials.validPassword(peer.security, password)) Text(stringResource(R.string.invalid_password))
            }
        }, confirmButton = {
            TextButton(enabled = WifiCredentials.validPassword(peer.security, password), onClick = {
                onJoinWifi(peer, password); password = ""; joining = null
            }) { Text(stringResource(R.string.join_wifi)) }
        }, dismissButton = { TextButton(onClick = { password = ""; joining = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
