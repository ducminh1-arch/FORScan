package com.carscan.core.spike

import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.models.PIDDefinition
import com.carscan.core.protocol.OBD2Service
import com.carscan.core.transport.Transport

data class SpikeReport(
    val isSuccess: Boolean,
    val adapterVersion: String,
    val batteryVoltage: Double,
    val vin: String,
    val sampleRpm: Double,
    val dtcCount: Int,
    val log: List<String>
)

class Phase0Spike {

    suspend fun run(transport: Transport): SpikeReport {
        val log = mutableListOf<String>()
        log.add("Starting Phase 0 Spike with transport: ${transport::class.simpleName}")

        // 1. Connect
        transport.connect()
        log.add("Transport connected successfully.")

        // 2. Initialize Driver
        val driver = ELM327Driver(transport)
        driver.initialize()
        log.add("Driver initialized. Adapter: '${driver.adapterVersion}', Voltage: ${driver.batteryVoltage}V")

        // 3. Read VIN via Mode 09 PID 02
        val obd2 = OBD2Service(driver)
        val vin = obd2.readVIN()
        log.add("VIN successfully decoded via ISO-TP: '$vin'")

        // 4. Sample Live RPM
        val rpmDef = PIDDefinition(
            id = "PID_010C",
            mode = "01",
            pid = "0C",
            name = "Engine RPM",
            shortName = "RPM",
            category = "Engine",
            module = "PCM",
            byteCount = 2,
            formula = "((A * 256) + B) / 4.0",
            unit = "RPM",
            min = 0.0,
            max = 8000.0,
            precision = 0,
            description = "Engine RPM"
        )
        val rpmVal = obd2.readPID(rpmDef)
        log.add("Sample Live RPM: ${rpmVal.formattedString} ${rpmVal.unit}")

        // 5. Read Stored DTCs
        val dtcs = obd2.readStoredDTCs()
        log.add("Stored DTCs read: ${dtcs.joinToString(", ") { it.code }}")

        return SpikeReport(
            isSuccess = true,
            adapterVersion = driver.adapterVersion,
            batteryVoltage = driver.batteryVoltage,
            vin = vin,
            sampleRpm = rpmVal.numericValue,
            dtcCount = dtcs.size,
            log = log
        )
    }
}
