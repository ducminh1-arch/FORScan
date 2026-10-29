package com.carscan.core.viewmodel

import com.carscan.core.models.LivePIDValue
import com.carscan.core.models.PIDDefinition
import com.carscan.core.transport.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GraphPoint(
    val timestampMs: Long,
    val value: Double
)

data class MinMax(
    var min: Double,
    var max: Double
)

class LiveViewModel(private val session: SessionManager = SessionManager.instance) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null

    val supportedPids = MutableStateFlow<List<PIDDefinition>>(emptyList())
    val selectedPidsForGraph = MutableStateFlow<List<String>>(listOf("PID_010C", "PID_010D"))
    val liveValues = MutableStateFlow<Map<String, LivePIDValue>>(emptyMap())
    val graphBuffers = MutableStateFlow<Map<String, List<GraphPoint>>>(emptyMap())
    val minMaxHistory = MutableStateFlow<Map<String, MinMax>>(emptyMap())
    val isPolling = MutableStateFlow(false)

    private val maxBufferPoints = 300

    init {
        loadDefaultSpecPids()
    }

    fun loadDefaultSpecPids() {
        supportedPids.value = listOf(
            PIDDefinition(id = "PID_010C", mode = "01", pid = "0C", name = "Engine RPM", shortName = "RPM", category = "Engine", module = "PCM", byteCount = 2, formula = "((A * 256) + B) / 4.0", unit = "RPM", min = 0.0, max = 8000.0, precision = 0, description = "Engine crankshaft RPM"),
            PIDDefinition(id = "PID_010D", mode = "01", pid = "0D", name = "Vehicle Speed", shortName = "Speed", category = "Speed/Fuel", module = "PCM", byteCount = 1, formula = "A", unit = "km/h", imperialUnit = "mph", min = 0.0, max = 255.0, precision = 0, description = "Vehicle speed"),
            PIDDefinition(id = "PID_0105", mode = "01", pid = "05", name = "Coolant Temp (ECT)", shortName = "ECT", category = "Temperature", module = "PCM", byteCount = 1, formula = "A - 40.0", unit = "°C", imperialUnit = "°F", min = -40.0, max = 215.0, precision = 1, description = "Coolant Temp"),
            PIDDefinition(id = "PID_010F", mode = "01", pid = "0F", name = "Intake Air Temp (IAT)", shortName = "IAT", category = "Temperature", module = "PCM", byteCount = 1, formula = "A - 40.0", unit = "°C", imperialUnit = "°F", min = -40.0, max = 215.0, precision = 1, description = "Intake Air Temp"),
            PIDDefinition(id = "PID_010B", mode = "01", pid = "0B", name = "Manifold Pressure (MAP)", shortName = "MAP", category = "Engine", module = "PCM", byteCount = 1, formula = "A", unit = "kPa", imperialUnit = "psi", min = 0.0, max = 255.0, precision = 1, description = "MAP"),
            PIDDefinition(id = "PID_0111", mode = "01", pid = "11", name = "Throttle Position (TP)", shortName = "Throttle", category = "Engine", module = "PCM", byteCount = 1, formula = "(A * 100.0) / 255.0", unit = "%", min = 0.0, max = 100.0, precision = 1, description = "Throttle"),
            PIDDefinition(id = "PID_0104", mode = "01", pid = "04", name = "Engine Load", shortName = "Load", category = "Engine", module = "PCM", byteCount = 1, formula = "(A * 100.0) / 255.0", unit = "%", min = 0.0, max = 100.0, precision = 1, description = "Engine Load"),
            PIDDefinition(id = "PID_012F", mode = "01", pid = "2F", name = "Fuel Level", shortName = "Fuel", category = "Speed/Fuel", module = "PCM", byteCount = 1, formula = "(A * 100.0) / 255.0", unit = "%", min = 0.0, max = 100.0, precision = 1, description = "Fuel Level"),
            PIDDefinition(id = "PID_0142", mode = "01", pid = "42", name = "Module Voltage (VPWR)", shortName = "VPWR", category = "Electrical", module = "PCM", byteCount = 2, formula = "((A * 256) + B) / 1000.0", unit = "V", min = 0.0, max = 20.0, precision = 2, description = "ECU Voltage")
        )
    }

    fun startPolling(pidsToPoll: List<PIDDefinition>) {
        stopPolling()
        if (session.connectionState.value !is ConnectionState.Connected) return
        val obd = session.obd2 ?: return

        isPolling.value = true
        pollJob = scope.launch(Dispatchers.IO) {
            val updatedLive = HashMap<String, LivePIDValue>()
            val updatedBuffers = HashMap(graphBuffers.value)
            val updatedHistory = HashMap(minMaxHistory.value)

            while (isActive && session.connectionState.value is ConnectionState.Connected) {
                try {
                    val imperial = session.isImperial.value
                    val batch = session.queue.execute {
                        obd.readPIDsBatch(pidsToPoll, imperial)
                    }

                    val now = System.currentTimeMillis()
                    for ((pidId, valObj) in batch) {
                        updatedLive[pidId] = valObj

                        // Record to CSV file if logging is active
                        if (session.isLoggingPIDs.value) {
                            val def = pidsToPoll.firstOrNull { it.id == pidId }
                            val pidCode = def?.pid ?: pidId
                            val pidName = def?.name ?: pidId
                            session.csvLogger.logPID(
                                pid = pidCode,
                                name = pidName,
                                value = valObj.numericValue,
                                unit = valObj.unit,
                                timestampMs = now
                            )
                        }

                        val hist = updatedHistory[pidId]
                        if (hist == null) {
                            updatedHistory[pidId] = MinMax(valObj.numericValue, valObj.numericValue)
                        } else {
                            hist.min = minOf(hist.min, valObj.numericValue)
                            hist.max = maxOf(hist.max, valObj.numericValue)
                        }

                        if (selectedPidsForGraph.value.contains(pidId)) {
                            val buf = (updatedBuffers[pidId] ?: emptyList()).toMutableList()
                            buf.add(GraphPoint(now, valObj.numericValue))
                            if (buf.size > maxBufferPoints) {
                                buf.removeAt(0)
                            }
                            updatedBuffers[pidId] = buf
                        }
                    }

                    liveValues.value = HashMap(updatedLive)
                    graphBuffers.value = HashMap(updatedBuffers)
                    minMaxHistory.value = HashMap(updatedHistory)
                } catch (_: Exception) {}

                delay(100L) // 100ms throttle
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        isPolling.value = false
    }

    fun toggleGraphPid(pidId: String) {
        val current = selectedPidsForGraph.value.toMutableList()
        if (current.contains(pidId)) {
            if (current.size > 1) {
                current.remove(pidId)
                selectedPidsForGraph.value = current
            }
        } else {
            if (current.size < 4) {
                current.add(pidId)
                selectedPidsForGraph.value = current
            }
        }
    }
}
