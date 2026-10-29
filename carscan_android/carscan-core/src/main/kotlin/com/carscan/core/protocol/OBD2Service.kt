package com.carscan.core.protocol

import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.models.DTCRecord
import com.carscan.core.models.LivePIDValue
import com.carscan.core.models.PIDDefinition
import com.carscan.core.transport.CommandFailedException

class OBD2Service(
    val driver: ELM327Driver,
    val isoTP: ISOTPHandler = ISOTPHandler(),
    val decoder: PIDDecoder = PIDDecoder()
) {

    suspend fun readVIN(): String {
        val raw = driver.executeWithRetry("0902", retries = 2, timeoutMs = 4000L)
        val messages = isoTP.reassemble(raw)

        for (msg in messages) {
            val p = msg.payload
            if (p.size >= 20 && (p[0].toInt() and 0xFF) == 0x49 && (p[1].toInt() and 0xFF) == 0x02) {
                val vinBytes = p.sliceArray(3 until 20)
                val vin = vinBytes.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
                if (vin.length == 17) {
                    return vin
                }
            }
        }

        for (msg in messages) {
            val ascii = msg.asciiString
            if (ascii.length >= 17) {
                return ascii.takeLast(17)
            }
        }

        throw CommandFailedException("Could not extract valid 17-character VIN from response: $raw")
    }

    suspend fun readPID(pid: PIDDefinition, imperial: Boolean = false): LivePIDValue {
        if (pid.id == "PID_ATRV") {
            val rawV = driver.executeWithRetry("ATRV")
            val v = rawV.replace("V", "").trim().toDoubleOrNull() ?: 0.0
            return LivePIDValue(
                pidId = pid.id,
                numericValue = v,
                formattedString = String.format("%.1f", v),
                unit = "V"
            )
        }

        val cmd = "${pid.mode}${pid.pid}"
        val raw = driver.executeWithRetry(cmd, retries = 1, timeoutMs = 2000L)
        val messages = isoTP.reassemble(raw)
        val firstMsg = messages.firstOrNull() ?: throw CommandFailedException("No payload for PID ${pid.name}")

        val dataBytes: ByteArray = when {
            firstMsg.payload.size >= 2 && (firstMsg.payload[0].toInt() and 0xFF) == 0x41 ->
                firstMsg.payload.sliceArray(2 until firstMsg.payload.size)
            firstMsg.payload.size >= 3 && (firstMsg.payload[0].toInt() and 0xFF) == 0x62 ->
                firstMsg.payload.sliceArray(3 until firstMsg.payload.size)
            else -> firstMsg.payload
        }

        val valCalculated = decoder.decode(pid, dataBytes, imperial)
            ?: throw CommandFailedException("Failed to decode value for ${pid.name}")

        val unit = (if (imperial) pid.imperialUnit else pid.unit) ?: pid.unit
        val prec = pid.precision ?: 1
        val fmt = String.format("%.${prec}f", valCalculated)

        return LivePIDValue(
            pidId = pid.id,
            numericValue = valCalculated,
            formattedString = fmt,
            unit = unit
        )
    }

    suspend fun discoverECUs(): List<String> {
        val prevHeader = driver.activeHeader
        try {
            driver.setHeader("7DF")
            val raw = driver.executeWithRetry("0100", retries = 2, timeoutMs = 2500L)
            val messages = isoTP.reassemble(raw)
            val detected = mutableSetOf<String>()
            for (msg in messages) {
                val canId = msg.canId?.uppercase()
                if (canId != null && canId in "7E8".."7EF") {
                    detected.add(canId)
                }
            }
            if (detected.isEmpty()) detected.add("7E8")
            return detected.sorted()
        } finally {
            try {
                driver.setHeader(prevHeader)
            } catch (_: Exception) {}
        }
    }

    suspend fun discoverSupportedPids(): Set<String> {
        val detector = SupportedPidsDetector()
        val supported = mutableSetOf<String>()
        var currentBase = 0x00

        while (currentBase <= 0xC0) {
            val cmd = String.format("01%02X", currentBase)
            val raw = try {
                driver.executeWithRetry(cmd, retries = 1, timeoutMs = 2000L)
            } catch (_: Exception) {
                break
            }
            val messages = isoTP.reassemble(raw)
            val first = messages.firstOrNull() ?: break
            if (first.payload.size < 6 || (first.payload[0].toInt() and 0xFF) != 0x41) break

            val maskBytes = first.payload.sliceArray(2 until minOf(6, first.payload.size))
            val result = detector.parseBitmask(maskBytes, currentBase)
            supported.addAll(result.supportedPids)

            if (!result.hasNextPage) break
            currentBase += 32
        }

        return supported
    }

    var maxBatchSize: Int = 6

    suspend fun readPIDsBatch(pids: List<PIDDefinition>, imperial: Boolean = false): Map<String, LivePIDValue> {
        if (pids.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, LivePIDValue>()
        val standardPids = pids.filter { it.mode == "01" }

        for (nonStandard in pids.filter { it.mode != "01" }) {
            try {
                results[nonStandard.id] = readPID(nonStandard, imperial)
            } catch (_: Exception) {}
        }

        if (standardPids.isEmpty()) return results

        var i = 0
        while (i < standardPids.size) {
            val batchSize = maxBatchSize.coerceAtLeast(1)
            val chunk = standardPids.subList(i, minOf(i + batchSize, standardPids.size))
            val cmd = "01" + chunk.joinToString("") { it.pid }

            val raw = try {
                driver.executeWithRetry(cmd, retries = 1, timeoutMs = 2000L)
            } catch (e: Exception) {
                if (maxBatchSize > 1) {
                    reduceBatchSize()
                    continue
                } else {
                    i++
                    continue
                }
            }

            val upperRaw = raw.uppercase()
            if (upperRaw.contains("?") || upperRaw.contains("BUFFER FULL")) {
                if (maxBatchSize > 1) {
                    reduceBatchSize()
                    continue
                } else {
                    i++
                    continue
                }
            }

            val messages = isoTP.reassemble(raw)
            val first = messages.firstOrNull()
            if (first == null || first.payload.size < 2 || (first.payload[0].toInt() and 0xFF) != 0x41) {
                if (maxBatchSize > 1) {
                    reduceBatchSize()
                    continue
                } else {
                    i++
                    continue
                }
            }

            val payload = first.payload
            var idx = 1
            var parsedAny = false
            while (idx < payload.size) {
                val pidHex = String.format("%02X", payload[idx].toInt() and 0xFF)
                idx++
                val targetDef = chunk.find { it.pid == pidHex } ?: break
                val bytesCount = targetDef.byteCount
                if (idx + bytesCount > payload.size) break
                val dataBytes = payload.sliceArray(idx until idx + bytesCount)
                idx += bytesCount

                val valCalc = decoder.decode(targetDef, dataBytes, imperial)
                if (valCalc != null) {
                    val unit = (if (imperial) targetDef.imperialUnit else targetDef.unit) ?: targetDef.unit
                    val prec = targetDef.precision ?: 1
                    val fmt = String.format("%.${prec}f", valCalc)
                    results[targetDef.id] = LivePIDValue(targetDef.id, numericValue = valCalc, formattedString = fmt, unit = unit)
                    parsedAny = true
                }
            }

            if (!parsedAny && maxBatchSize > 1) {
                reduceBatchSize()
                continue
            }

            i += chunk.size
        }

        return results
    }

    private fun reduceBatchSize() {
        maxBatchSize = when {
            maxBatchSize > 4 -> 4
            maxBatchSize > 2 -> 2
            else -> 1
        }
    }

    suspend fun readStoredDTCs(): List<DTCRecord> {
        return readDTCsByMode("03", 0x43, "Confirmed")
    }

    suspend fun readPendingDTCs(): List<DTCRecord> {
        return readDTCsByMode("07", 0x47, "Pending")
    }

    suspend fun readPermanentDTCs(): List<DTCRecord> {
        return readDTCsByMode("0A", 0x4A, "Permanent")
    }

    suspend fun clearAllDTCs() {
        driver.executeWithRetry("04", retries = 2, timeoutMs = 4000L)
    }

    private suspend fun readDTCsByMode(command: String, expectedPrefix: Int, defaultType: String): List<DTCRecord> {
        val raw = driver.executeWithRetry(command, retries = 2, timeoutMs = 3000L)
        val messages = isoTP.reassemble(raw)
        val records = mutableListOf<DTCRecord>()

        for (msg in messages) {
            val p = msg.payload
            if (p.size < 2 || (p[0].toInt() and 0xFF) != expectedPrefix) continue
            val count = p[1].toInt() and 0xFF
            var idx = 2
            while (idx + 1 < p.size && records.size < count) {
                val b0 = p[idx].toInt() and 0xFF
                val b1 = p[idx + 1].toInt() and 0xFF
                idx += 2
                if (b0 == 0 && b1 == 0) continue

                val code = PIDDecoder.decodeDTC(b0, b1)
                val prefix = code.take(1)

                records.add(
                    DTCRecord(
                        code = code,
                        system = if (prefix == "P") "Powertrain" else if (prefix == "B") "Body" else "Chassis",
                        description = "$defaultType Diagnostic Trouble Code",
                        severity = if (defaultType == "Permanent") "High" else if (defaultType == "Confirmed") "Medium" else "Low"
                    )
                )
            }
        }
        return records
    }

}
