package com.carscan.core.protocol

import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.models.DTCRecord
import com.carscan.core.models.ModuleDefinition
import com.carscan.core.models.ModuleScanResult
import com.carscan.core.models.ModuleScanStatus
import com.carscan.core.transport.CommandFailedException
import com.carscan.core.transport.NoDataException
import com.carscan.core.transport.TimeoutException
import com.carscan.core.transport.TransportException
import kotlinx.coroutines.delay

class UDSService(
    val driver: ELM327Driver,
    val isoTP: ISOTPHandler = ISOTPHandler()
) {

    /**
     * DiagnosticSessionControl (0x10): strictly default session (0x01).
     * No security access (0x27) is permitted or implemented.
     */
    suspend fun startSession(sessionType: Int = 0x01) {
        val cmd = String.format("10%02X", sessionType)
        val raw = driver.executeWithRetry(cmd, retries = 2)
        val messages = isoTP.reassemble(raw)
        val first = messages.firstOrNull() ?: throw CommandFailedException("No response for session control")
        if (first.payload.isEmpty() || (first.payload[0].toInt() and 0xFF) != 0x50) {
            throw CommandFailedException("Session control rejected: $raw")
        }
    }

    /**
     * Read Diagnostic Trouble Codes (UDS 0x19 0x02 reportDTCByStatusMask) for a specific module.
     * Sets the CAN header to the target module's txId.
     * Correctly handles NRC 0x78 (ResponsePending) without failing.
     * Accurately distinguishes between:
     * - Unresponsive / offline module (NO DATA / Timeout) -> ModuleScanStatus.OfflineNoResponse
     * - Module responding with zero DTCs -> ModuleScanStatus.Clean
     * - Module responding with faults -> ModuleScanStatus.FaultsFound
     */
    suspend fun readDTCsForModule(
        module: ModuleDefinition,
        statusMask: Int = 0xAF
    ): ModuleScanResult {
        try {
            driver.setHeader(module.txId)
        } catch (e: Exception) {
            return ModuleScanResult(module, ModuleScanStatus.ScanError("Failed to set header: ${e.message}"))
        }

        val cmd = String.format("1902%02X", statusMask)
        var rawResponse: String
        try {
            rawResponse = driver.executeWithRetry(cmd, retries = 2, timeoutMs = 4000L)
        } catch (noData: NoDataException) {
            return ModuleScanResult(module, ModuleScanStatus.OfflineNoResponse)
        } catch (timeout: TimeoutException) {
            return ModuleScanResult(module, ModuleScanStatus.OfflineNoResponse)
        } catch (te: TransportException) {
            return ModuleScanResult(module, ModuleScanStatus.ScanError(te.message ?: "Transport error"))
        } catch (e: Exception) {
            return ModuleScanResult(module, ModuleScanStatus.ScanError(e.message ?: "Unknown error"))
        }

        var messages = isoTP.reassemble(rawResponse)

        // Check for NRC 0x78 (RequestCorrectlyReceived - ResponsePending)
        val hasNrc78 = messages.any { msg ->
            val p = msg.payload
            p.size >= 3 && (p[0].toInt() and 0xFF) == 0x7F && (p[1].toInt() and 0xFF) == 0x19 && (p[2].toInt() and 0xFF) == 0x78
        }

        // If NRC 0x78 was received and no positive response (0x59) in current stream, wait for subsequent frame
        val hasPositive = messages.any { msg ->
            val p = msg.payload
            p.isNotEmpty() && (p[0].toInt() and 0xFF) == 0x59
        }

        if (hasNrc78 && !hasPositive) {
            // Server needs more time, wait and read again
            delay(400)
            try {
                val nextRaw = driver.executeWithRetry(cmd, retries = 1, timeoutMs = 3000L)
                messages = isoTP.reassemble(nextRaw)
            } catch (_: Exception) {}
        }

        // Filter out NRC 0x78 frames and process
        val positiveMsg = messages.firstOrNull { msg ->
            val p = msg.payload
            p.size >= 2 && (p[0].toInt() and 0xFF) == 0x59 && (p[1].toInt() and 0xFF) == 0x02
        }

        if (positiveMsg == null) {
            // Check if any other negative response occurred
            val negMsg = messages.firstOrNull { msg ->
                val p = msg.payload
                p.size >= 3 && (p[0].toInt() and 0xFF) == 0x7F
            }
            if (negMsg != null) {
                val nrc = negMsg.payload[2].toInt() and 0xFF
                if (nrc != 0x78) {
                    return ModuleScanResult(module, ModuleScanStatus.ScanError(String.format("UDS NRC 0x%02X", nrc)))
                }
            }
            return ModuleScanResult(module, ModuleScanStatus.OfflineNoResponse)
        }

        val p = positiveMsg.payload
        // 59 02 [AvailabilityMask] [DTC1 3-bytes + 1-byte status] ...
        if (p.size <= 3) {
            // Positive response with zero DTCs
            return ModuleScanResult(module, ModuleScanStatus.Clean)
        }

        val records = mutableListOf<DTCRecord>()
        var idx = 3
        while (idx + 3 < p.size) {
            val b0 = p[idx].toInt() and 0xFF
            val b1 = p[idx + 1].toInt() and 0xFF
            val b2 = p[idx + 2].toInt() and 0xFF
            val status = p[idx + 3].toInt() and 0xFF
            idx += 4

            val baseCode = PIDDecoder.decodeDTC(b0, b1)
            val subFault = String.format("%02X", b2)
            val fullCode = if (b2 != 0) "$baseCode:$subFault" else baseCode
            val prefix = baseCode.take(1)

            records.add(
                DTCRecord(
                    code = fullCode,
                    system = when (prefix) {
                        "B" -> "Body"
                        "C" -> "Chassis"
                        "U" -> "Network"
                        else -> "Powertrain"
                    },
                    description = "UDS Diagnostic Trouble Code",
                    severity = if ((status and 0x08) != 0) "Confirmed" else "Pending",
                    statusByte = status
                )
            )
        }

        return if (records.isEmpty()) {
            ModuleScanResult(module, ModuleScanStatus.Clean)
        } else {
            ModuleScanResult(module, ModuleScanStatus.FaultsFound(records))
        }
    }

    /**
     * Clear Diagnostic Information (UDS 0x14 0xFF 0xFF 0xFF).
     * STRICTLY executed per individual module (txId). NEVER broadcast to 7DF.
     * Handles NRC 0x78 (ResponsePending).
     */
    suspend fun clearDTCsForModule(module: ModuleDefinition): Boolean {
        require(module.txId.uppercase() != "7DF") {
            "Safety restriction: UDS 0x14 ClearDTC must never be broadcast to 7DF. Target specific module only."
        }

        driver.setHeader(module.txId)
        val cmd = "14FFFFFF"
        val raw = driver.executeWithRetry(cmd, retries = 2, timeoutMs = 4500L)
        var messages = isoTP.reassemble(raw)

        val hasNrc78 = messages.any { msg ->
            val p = msg.payload
            p.size >= 3 && (p[0].toInt() and 0xFF) == 0x7F && (p[1].toInt() and 0xFF) == 0x14 && (p[2].toInt() and 0xFF) == 0x78
        }

        if (hasNrc78 && messages.none { it.payload.firstOrNull()?.toInt()?.and(0xFF) == 0x54 }) {
            delay(400)
            try {
                val nextRaw = driver.executeWithRetry(cmd, retries = 1, timeoutMs = 3000L)
                messages = isoTP.reassemble(nextRaw)
            } catch (_: Exception) {}
        }

        val successMsg = messages.firstOrNull { msg ->
            msg.payload.isNotEmpty() && (msg.payload[0].toInt() and 0xFF) == 0x54
        } ?: throw CommandFailedException("Module ${module.acronym} rejected DTC clear: $raw")

        return true
    }

    suspend fun readDTCsByStatusMask(statusMask: Int = 0xAF): List<DTCRecord> {
        val cmd = String.format("1902%02X", statusMask)
        val raw = driver.executeWithRetry(cmd, retries = 2, timeoutMs = 3500L)
        val messages = isoTP.reassemble(raw)
        val records = mutableListOf<DTCRecord>()

        for (msg in messages) {
            val p = msg.payload
            if (p.size < 3 || (p[0].toInt() and 0xFF) != 0x59 || (p[1].toInt() and 0xFF) != 0x02) continue
            var idx = 3
            while (idx + 3 < p.size) {
                val b0 = p[idx].toInt() and 0xFF
                val b1 = p[idx + 1].toInt() and 0xFF
                val b2 = p[idx + 2].toInt() and 0xFF
                val status = p[idx + 3].toInt() and 0xFF
                idx += 4

                val code = PIDDecoder.decodeDTC(b0, b1)
                val prefix = code.take(1)

                records.add(
                    DTCRecord(
                        code = code,
                        system = if (prefix == "B") "Body" else if (prefix == "C") "Chassis" else "Powertrain",
                        description = "UDS Diagnostic Trouble Code",
                        severity = if ((status and 0x08) != 0) "High" else "Medium",
                        statusByte = status
                    )
                )
            }
        }
        return records
    }

    suspend fun ecuReset(resetType: Int = 0x01) {
        val cmd = String.format("11%02X", resetType)
        val raw = driver.executeWithRetry(cmd, retries = 1, timeoutMs = 3000L)
        val messages = isoTP.reassemble(raw)
        val first = messages.firstOrNull() ?: throw CommandFailedException("No response for ECU reset")
        if (first.payload.isEmpty() || (first.payload[0].toInt() and 0xFF) != 0x51) {
            throw CommandFailedException("ECU reset rejected: $raw")
        }
    }

    suspend fun startRoutine(routineId: String) {
        val cmd = "3101${routineId.uppercase()}"
        val raw = driver.executeWithRetry(cmd, retries = 1, timeoutMs = 5000L)
        val messages = isoTP.reassemble(raw)
        val first = messages.firstOrNull() ?: throw CommandFailedException("No response for routine start")
        if (first.payload.isEmpty() || (first.payload[0].toInt() and 0xFF) != 0x71) {
            throw CommandFailedException("Routine start rejected: $raw")
        }
    }

    suspend fun testerPresent() {
        val raw = driver.executeWithRetry("3E00", retries = 1, timeoutMs = 1500L)
        val messages = isoTP.reassemble(raw)
        val first = messages.firstOrNull() ?: throw CommandFailedException("No response for TesterPresent")
        if (first.payload.isEmpty() || (first.payload[0].toInt() and 0xFF) != 0x7E) {
            throw CommandFailedException("TesterPresent rejected")
        }
    }
}
