package com.carscan.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.carscan.core.viewmodel.ConnectionType
import com.carscan.core.viewmodel.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: SessionManager = SessionManager.instance,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val connectionType by session.connectionType.collectAsState()
    val isDemoMode by session.isDemoMode.collectAsState()
    val isImperial by session.isImperial.collectAsState()
    val wifiHost by session.wifiHost.collectAsState()
    val wifiPort by session.wifiPort.collectAsState()
    val selectedAddress by session.selectedBluetoothAddress.collectAsState()
    val selectedName by session.selectedBluetoothName.collectAsState()

    var wifiHostInput by remember(wifiHost) { mutableStateOf(wifiHost) }
    var wifiPortInput by remember(wifiPort) { mutableStateOf(wifiPort.toString()) }

    var isScanningBle by remember { mutableStateOf(false) }
    val discoveredBleDevices = remember { mutableStateListOf<DiscoveredBleDevice>() }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    var permissionErrorMessage by remember { mutableStateOf<String?>(null) }

    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    // Required permissions depending on Android version
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            permissionErrorMessage = null
            if (connectionType == ConnectionType.BLUETOOTH_CLASSIC) {
                // Refresh bonded devices
                pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            } else if (connectionType == ConnectionType.BLE) {
                // Start BLE scan
                startBleScan(context, bluetoothAdapter, discoveredBleDevices) { scanning ->
                    isScanningBle = scanning
                }
            }
        } else {
            permissionErrorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Bluetooth Connect & Scan permissions are required to discover and connect to OBD adapters."
            } else {
                "Location and Bluetooth permissions are required to scan for OBD adapters on Android 11 and lower."
            }
        }
    }

    fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permissionErrorMessage = null
            onGranted()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Load bonded devices when screen opens with BT Classic
    LaunchedEffect(connectionType) {
        if (connectionType == ConnectionType.BLUETOOTH_CLASSIC) {
            checkAndRequestPermissions {
                pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF14171E))
            )
        },
        containerColor = Color(0xFF0F1115)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Error Alert
            permissionErrorMessage?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1818)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color(0xFFFF5252))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Permission Denied", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(err, color = Color(0xFFFFCDD2), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    checkAndRequestPermissions {
                                        permissionErrorMessage = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                            ) {
                                Text("Grant Permissions", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Connection Type Selection
            item {
                Text(
                    "ADAPTER CONNECTION TYPE",
                    color = Color(0xFF78909C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B1E26), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val types = listOf(
                        ConnectionType.WIFI to "Wi-Fi",
                        ConnectionType.BLUETOOTH_CLASSIC to "BT Classic",
                        ConnectionType.BLE to "BLE"
                    )

                    types.forEach { (type, label) ->
                        val isSelected = connectionType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) Color(0xFF2C3240) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF4FC3F7) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    session.connectionType.value = type
                                    if (type == ConnectionType.BLUETOOTH_CLASSIC) {
                                        checkAndRequestPermissions {
                                            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color(0xFF4FC3F7) else Color(0xFF90A4AE),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Wi-Fi Configuration
            if (connectionType == ConnectionType.WIFI) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Wi-Fi Settings (ELM327 TCP)", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = wifiHostInput,
                                onValueChange = {
                                    wifiHostInput = it
                                    session.wifiHost.value = it
                                },
                                label = { Text("IP Address") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4FC3F7),
                                    unfocusedBorderColor = Color(0xFF37474F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = wifiPortInput,
                                onValueChange = {
                                    wifiPortInput = it
                                    val p = it.toIntOrNull()
                                    if (p != null) session.wifiPort.value = p
                                },
                                label = { Text("TCP Port") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4FC3F7),
                                    unfocusedBorderColor = Color(0xFF37474F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Standard Wi-Fi OBD adapters default to 192.168.0.10:35000.",
                                color = Color(0xFF78909C),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Bluetooth Classic Configuration
            if (connectionType == ConnectionType.BLUETOOTH_CLASSIC) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Bluetooth Classic (SPP)", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        selectedName?.let { "$it ($selectedAddress)" } ?: "No device selected",
                                        color = if (selectedAddress != null) Color(0xFF4FC3F7) else Color(0xFFFFB74D),
                                        fontSize = 13.sp
                                    )
                                }
                                Button(
                                    onClick = {
                                        checkAndRequestPermissions {
                                            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3240))
                                ) {
                                    Text("Refresh", color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "PAIRED OBD ADAPTERS (TAP TO SELECT)",
                                color = Color(0xFF78909C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (pairedDevices.isEmpty()) {
                                Text(
                                    "No paired Bluetooth devices found. Pair your OBDII adapter in Android Bluetooth settings first.",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 12.sp
                                )
                            } else {
                                pairedDevices.forEach { dev ->
                                    val isCur = dev.address == selectedAddress
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isCur) Color(0xFF263238) else Color(0xFF14171E),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isCur) Color(0xFF4FC3F7) else Color(0xFF263238),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                session.selectedBluetoothAddress.value = dev.address
                                                session.selectedBluetoothName.value = dev.name ?: "OBDII Device"
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(dev.name ?: "Unknown OBD Device", color = Color.White, fontWeight = FontWeight.Medium)
                                            Text(dev.address, color = Color(0xFF78909C), fontSize = 12.sp)
                                        }
                                        if (isCur) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF4FC3F7))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // BLE Configuration
            if (connectionType == ConnectionType.BLE) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Bluetooth Low Energy (BLE)", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        selectedName?.let { "$it ($selectedAddress)" } ?: "No device selected",
                                        color = if (selectedAddress != null) Color(0xFF4FC3F7) else Color(0xFFFFB74D),
                                        fontSize = 13.sp
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (!isScanningBle) {
                                            checkAndRequestPermissions {
                                                startBleScan(context, bluetoothAdapter, discoveredBleDevices) { scanning ->
                                                    isScanningBle = scanning
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isScanningBle,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                                ) {
                                    if (isScanningBle) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scanning...", color = Color.White, fontSize = 12.sp)
                                    } else {
                                        Text("Scan BLE", color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "DISCOVERED BLE DEVICES (TAP TO SELECT)",
                                color = Color(0xFF78909C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (discoveredBleDevices.isEmpty()) {
                                Text(
                                    if (isScanningBle) "Searching for nearby BLE OBD adapters (Vgate, iCar, etc.)..."
                                    else "Press 'Scan BLE' to search for Bluetooth Low Energy adapters.",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 12.sp
                                )
                            } else {
                                discoveredBleDevices.forEach { ble ->
                                    val isCur = ble.address == selectedAddress
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isCur) Color(0xFF263238) else Color(0xFF14171E),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isCur) Color(0xFF4FC3F7) else Color(0xFF263238),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                session.selectedBluetoothAddress.value = ble.address
                                                session.selectedBluetoothName.value = ble.name
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(ble.name, color = Color.White, fontWeight = FontWeight.Medium)
                                            Text("${ble.address} • RSSI: ${ble.rssi} dBm", color = Color(0xFF78909C), fontSize = 12.sp)
                                        }
                                        if (isCur) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF4FC3F7))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // General Preferences
            item {
                Text(
                    "PREFERENCES",
                    color = Color(0xFF78909C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Demo Simulation Mode", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Simulate Ford EcoBoost without physical adapter", color = Color(0xFF78909C), fontSize = 12.sp)
                            }
                            Switch(
                                checked = isDemoMode,
                                onCheckedChange = { session.isDemoMode.value = it }
                            )
                        }

                        Divider(color = Color(0xFF263238), modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Imperial Units (°F, psi, mph)", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Default is Metric (°C, kPa, km/h)", color = Color(0xFF78909C), fontSize = 12.sp)
                            }
                            Switch(
                                checked = isImperial,
                                onCheckedChange = { session.isImperial.value = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun startBleScan(
    context: Context,
    adapter: BluetoothAdapter?,
    resultsList: MutableList<DiscoveredBleDevice>,
    onScanningState: (Boolean) -> Unit
) {
    val scanner = adapter?.bluetoothLeScanner ?: return
    resultsList.clear()
    onScanningState(true)

    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { dev ->
                val addr = dev.address
                val name = try {
                    dev.name ?: result.scanRecord?.deviceName ?: "OBD BLE Device"
                } catch (_: SecurityException) {
                    "OBD BLE Device"
                }

                if (resultsList.none { it.address == addr }) {
                    resultsList.add(DiscoveredBleDevice(dev, name, addr, result.rssi))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            onScanningState(false)
        }
    }

    try {
        scanner.startScan(callback)
    } catch (_: SecurityException) {
        onScanningState(false)
        return
    }

    // Auto-stop scanning after 10 seconds
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {}
        onScanningState(false)
    }, 10000L)
}
