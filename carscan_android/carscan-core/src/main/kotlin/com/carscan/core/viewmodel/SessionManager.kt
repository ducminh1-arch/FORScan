package com.carscan.core.viewmodel

import com.carscan.core.adapter.CommandQueue
import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.models.AdapterProfile
import com.carscan.core.models.VehicleInfo
import com.carscan.core.protocol.OBD2Service
import com.carscan.core.protocol.UDSService
import com.carscan.core.logging.CSVLogService
import com.carscan.core.logging.RawTraceLogger
import com.carscan.core.transport.ConnectionState
import com.carscan.core.transport.MockTransport
import com.carscan.core.transport.Transport
import com.carscan.core.transport.WiFiTransport
import kotlinx.coroutines.CoroutineScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.carscan.core.transport.BtClassicTransport
import com.carscan.core.transport.BleTransport
import com.carscan.core.transport.TransportException

enum class ConnectionType {
    WIFI,
    BLUETOOTH_CLASSIC,
    BLE
}

class SessionManager {
    companion object {
        val instance = SessionManager()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val isDemoMode = MutableStateFlow(true)
    val isImperial = MutableStateFlow(false)
    val connectionType = MutableStateFlow(ConnectionType.WIFI)
    val wifiHost = MutableStateFlow("192.168.0.10")
    val wifiPort = MutableStateFlow(35000)
    val selectedBluetoothAddress = MutableStateFlow<String?>(null)
    val selectedBluetoothName = MutableStateFlow<String?>(null)

    val vehicleInfo = MutableStateFlow<VehicleInfo?>(null)
    val adapterVoltage = MutableStateFlow(0.0)
    val ecuVoltage = MutableStateFlow(0.0)
    val errorMessage = MutableStateFlow<String?>(null)
    val adapterProfile = MutableStateFlow<AdapterProfile?>(null)
    val currentBus = MutableStateFlow("HS-CAN")
    val manualSwitchPrompt = MutableStateFlow<String?>(null)

    val queue = CommandQueue()
    var driver: ELM327Driver? = null
        private set
    var obd2: OBD2Service? = null
        private set
    var uds: UDSService? = null
        private set
    private var activeTransport: Transport? = null

    val isLoggingPIDs = MutableStateFlow(false)
    val isTracingCommands = MutableStateFlow(false)
    val csvLogger = CSVLogService()
    val rawTraceLogger = RawTraceLogger()

    fun startPIDLogging(outputDir: File, sessionName: String? = null): File {
        val file = csvLogger.startLogging(outputDir, sessionName)
        isLoggingPIDs.value = true
        return file
    }

    fun stopPIDLogging(): File? {
        val file = csvLogger.stopLogging()
        isLoggingPIDs.value = false
        return file
    }

    fun startRawTracing(outputDir: File, name: String? = null): File {
        val file = rawTraceLogger.startTrace(outputDir, name)
        isTracingCommands.value = true
        driver?.traceLogger = rawTraceLogger
        return file
    }

    fun stopRawTracing(): File? {
        val file = rawTraceLogger.stopTrace()
        isTracingCommands.value = false
        driver?.traceLogger = null
        return file
    }


    var appContext: android.content.Context? = null

    fun connect(): kotlinx.coroutines.Job? {
        if (_connectionState.value is ConnectionState.Connected || _connectionState.value is ConnectionState.Connecting) return null

        _connectionState.value = ConnectionState.Connecting
        errorMessage.value = null

        return scope.launch(Dispatchers.IO) {
            try {
                val transport: Transport = if (isDemoMode.value) {
                    MockTransport(simulatedVoltage = 12.6)
                } else {
                    when (connectionType.value) {
                        ConnectionType.WIFI -> {
                            WiFiTransport(host = wifiHost.value, port = wifiPort.value)
                        }
                        ConnectionType.BLUETOOTH_CLASSIC -> {
                            val mac = selectedBluetoothAddress.value
                                ?: throw TransportException("No Bluetooth device selected. Please select a paired OBD adapter in Settings.")
                            BtClassicTransport(macAddress = mac)
                        }
                        ConnectionType.BLE -> {
                            val mac = selectedBluetoothAddress.value
                                ?: throw TransportException("No BLE device selected. Please scan and select an adapter in Settings.")
                            BleTransport(macAddress = mac, context = appContext)
                        }
                    }
                }
                activeTransport = transport

                transport.connect()
                val drv = ELM327Driver(transport)
                if (isTracingCommands.value) {
                    drv.traceLogger = rawTraceLogger
                }
                drv.initialize()
                val obd = OBD2Service(drv)
                val u = UDSService(drv)

                driver = drv
                obd2 = obd
                uds = u
                adapterVoltage.value = drv.batteryVoltage
                adapterProfile.value = drv.adapterProfile
                currentBus.value = drv.currentBus
                _connectionState.value = ConnectionState.Connected

                fetchInitialVehicleData()
            } catch (e: Exception) {
                val err = e.message ?: "Failed to connect"
                errorMessage.value = "Failed to connect to adapter: $err"
                cleanupResources()
                _connectionState.value = ConnectionState.Error(err)
            }
        }
    }

    suspend fun switchBus(targetBus: String) {
        val drv = driver ?: return
        queue.execute {
            drv.switchBus(targetBus) { prompt ->
                manualSwitchPrompt.value = prompt
            }
            currentBus.value = drv.currentBus
        }
    }

    fun dismissManualSwitchPrompt() {
        manualSwitchPrompt.value = null
    }

    private fun cleanupResources() {
        queue.cancelAll()
        activeTransport?.disconnect()
        activeTransport = null
        driver = null
        obd2 = null
        uds = null
        adapterProfile.value = null
        queue.reset()
    }

    fun disconnect() {
        cleanupResources()
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun fetchInitialVehicleData() {
        val obd = obd2 ?: return
        try {
            val vin = queue.execute { obd.readVIN() }
            val ecus = queue.execute { obd.discoverECUs() }

            val decodedYear = com.carscan.core.protocol.VINDecoder.decodeModelYear(vin) ?: 2021
            vehicleInfo.value = VehicleInfo(
                vin = vin,
                make = com.carscan.core.protocol.VINDecoder.decodeMake(vin),
                model = if (vin.startsWith("1FA")) "Mustang EcoBoost" else "Focus",
                year = decodedYear,
                batteryVoltage = if (adapterVoltage.value > 0) adapterVoltage.value else 12.6,
                detectedModules = ecus
            )
        } catch (_: Exception) {}
    }
}
