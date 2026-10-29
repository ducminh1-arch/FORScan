package com.carscan.core.viewmodel

import com.carscan.core.models.PIDDefinition
import com.carscan.core.transport.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class DetectedModuleItem(
    val canId: String,
    val name: String,
    val acronym: String,
    val bus: String
)

class VehicleViewModel(private val session: SessionManager = SessionManager.instance) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val vin = MutableStateFlow("Not Read")
    val make = MutableStateFlow("Ford")
    val model = MutableStateFlow("Mustang EcoBoost")
    val year = MutableStateFlow("2021")
    val atrvVoltage = MutableStateFlow(0.0)
    val vpwrVoltage = MutableStateFlow(0.0)
    val detectedModules = MutableStateFlow<List<DetectedModuleItem>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val errorText = MutableStateFlow<String?>(null)

    fun loadData() {
        if (session.connectionState.value !is ConnectionState.Connected) {
            errorText.value = "Adapter not connected. Please connect from Home screen."
            return
        }

        val obd = session.obd2 ?: return
        isLoading.value = true
        errorText.value = null

        scope.launch(Dispatchers.IO) {
            try {
                // 1. ATRV voltage
                try {
                    val rawV = session.queue.execute { obd.driver.executeWithRetry("ATRV") }
                    val cleanV = rawV.replace("V", "").trim().toDoubleOrNull() ?: 12.6
                    atrvVoltage.value = cleanV
                } catch (_: Exception) {}

                // 2. VPWR (PID 0142)
                try {
                    val vpwrDef = PIDDefinition(
                        id = "PID_0142", mode = "01", pid = "42", name = "Control Module Voltage",
                        shortName = "VPWR", category = "Electrical", module = "PCM", byteCount = 2,
                        formula = "((A * 256) + B) / 1000.0", unit = "V", min = 0.0, max = 20.0,
                        precision = 2, description = ""
                    )
                    val vpwr = session.queue.execute { obd.readPID(vpwrDef) }
                    vpwrVoltage.value = vpwr.numericValue
                } catch (_: Exception) {}

                // 3. Read VIN (09 02)
                val vinStr = session.queue.execute { obd.readVIN() }
                vin.value = vinStr
                val decodedYear = com.carscan.core.protocol.VINDecoder.decodeModelYear(vinStr)
                if (decodedYear != null) {
                    year.value = decodedYear.toString()
                }
                make.value = com.carscan.core.protocol.VINDecoder.decodeMake(vinStr)
                if (vinStr.startsWith("1FA")) {
                    model.value = "Mustang EcoBoost"
                } else if (vinStr.startsWith("WF0")) {
                    model.value = "Focus"
                }

                // 4. Discover Standard ECUs (7E8..7EF) via 7DF
                val ecus = session.queue.execute { obd.discoverECUs() }
                val items = ecus.map { id ->
                    when (id) {
                        "7E8" -> DetectedModuleItem(id, "Powertrain Control Module", "PCM", "HS-CAN")
                        "7E9" -> DetectedModuleItem(id, "Transmission Control Module", "TCM", "HS-CAN")
                        else -> DetectedModuleItem(id, "Electronic Control Unit ($id)", "ECU", "HS-CAN")
                    }
                }
                detectedModules.value = items
            } catch (e: Exception) {
                errorText.value = "Vehicle scan error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
