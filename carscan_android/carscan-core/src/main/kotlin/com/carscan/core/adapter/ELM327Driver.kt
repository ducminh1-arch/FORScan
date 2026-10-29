package com.carscan.core.adapter

import com.carscan.core.models.AdapterFamily
import com.carscan.core.models.AdapterProfile
import com.carscan.core.transport.*
import kotlinx.coroutines.delay


class ELM327Driver(val transport: Transport) {
    var isInitialized: Boolean = false
        private set
    var adapterVersion: String = ""
        private set
    var batteryVoltage: Double = 0.0
        private set
    var activeHeader: String = "7E0"
        private set
    var currentBus: String = "HS-CAN"
        private set
    var adapterProfile: AdapterProfile = AdapterProfile(
        id = "ELM327_GENERIC",
        name = "Standard ELM327",
        family = AdapterFamily.ELM327_GENERIC,
        autoBusSwitch = false,
        verified = false
    )
        private set

    var traceLogger: com.carscan.core.logging.RawTraceLogger? = null

    suspend fun initialize() {
        // Step 1: Software Reset
        val resetResp = executeWithRetry("ATZ", retries = 2, timeoutMs = 3000L)
        adapterVersion = cleanResponse(resetResp)

        // Step 2: Echo Off
        executeWithRetry("ATE0", retries = 2)

        // Step 3: Linefeeds Off
        executeWithRetry("ATL0", retries = 2)

        // Step 4: Spaces Off
        executeWithRetry("ATS0", retries = 2)

        // Step 5: Headers On
        executeWithRetry("ATH1", retries = 2)

        // Step 6: Set Protocol ISO 15765-4 (CAN 11/500)
        executeWithRetry("ATSP6", retries = 2)
        currentBus = "HS-CAN"

        // Step 7: Read Voltage
        try {
            val vStr = executeWithRetry("ATRV", retries = 1)
            batteryVoltage = parseVoltage(vStr)
        } catch (_: Exception) {}

        // Step 8: Detect Adapter Profile (STN, vLinker, or generic ELM327)
        adapterProfile = detectAdapterProfile()

        isInitialized = true
    }

    suspend fun detectAdapterProfile(): AdapterProfile {
        // 1. Try ATI
        try {
            val atiResp = cleanResponse(executeWithRetry("ATI", retries = 1, timeoutMs = 1500L))
            if (atiResp.contains("vLinker", ignoreCase = true) || atiResp.contains("Vgate", ignoreCase = true)) {
                return AdapterProfile(
                    id = "VLINKER_FS",
                    name = "vLinker (FS/MC/FD)",
                    family = AdapterFamily.VLINKER_FS,
                    autoBusSwitch = true,
                    verified = false
                )
            }
        } catch (_: Exception) {}

        // 2. Try STI / STDI (STN command family)
        try {
            val stiResp = cleanResponse(executeWithRetry("STI", retries = 1, timeoutMs = 1500L))
            if (stiResp.contains("STN", ignoreCase = true) || stiResp.contains("OBDLink", ignoreCase = true)) {
                var modelName = "OBDLink (STN)"
                var hasMsCan = true
                var autoBusSwitch = true

                try {
                    val stdiResp = cleanResponse(executeWithRetry("STDI", retries = 1, timeoutMs = 1500L))
                    val combined = "$stiResp $stdiResp".uppercase()
                    if (combined.contains("LX") || combined.contains("CX") || combined.contains("SX") || combined.contains("STN1110")) {
                        // OBDLink LX, CX, SX, and STN1110 do not have MS-CAN transceiver (HS-CAN only)
                        hasMsCan = false
                        autoBusSwitch = false
                        modelName = if (combined.contains("CX")) "OBDLink CX" else if (combined.contains("LX")) "OBDLink LX" else "OBDLink (HS-CAN Only)"
                    } else if (combined.contains("EX")) {
                        hasMsCan = true
                        modelName = "OBDLink EX"
                    } else if (combined.contains("MX+")) {
                        hasMsCan = true
                        modelName = "OBDLink MX+"
                    } else if (combined.contains("MX")) {
                        hasMsCan = true
                        modelName = "OBDLink MX"
                    }
                } catch (_: Exception) {}

                return AdapterProfile(
                    id = "OBDLINK_STN",
                    name = modelName,
                    family = AdapterFamily.OBDLINK_STN,
                    autoBusSwitch = autoBusSwitch,
                    verified = false,
                    hasMsCan = hasMsCan
                )
            }
        } catch (_: Exception) {}

        return AdapterProfile(
            id = "ELM327_GENERIC",
            name = "Standard ELM327",
            family = AdapterFamily.ELM327_GENERIC,
            autoBusSwitch = false,
            verified = false,
            hasMsCan = true
        )
    }

    suspend fun switchBus(
        targetBus: String,
        onManualSwitchPrompt: ((String) -> Unit)? = null
    ) {
        val normalized = targetBus.uppercase()
        if (normalized == currentBus.uppercase()) return

        try {
            when (normalized) {
                "MS-CAN" -> {
                    if (!adapterProfile.hasMsCan) {
                        throw BusSwitchFailedException("Adapter does not support MS-CAN (${adapterProfile.name}). Bus switch aborted.")
                    }
                    when (adapterProfile.family) {
                        AdapterFamily.OBDLINK_STN -> {
                            // STP 53: ISO 15765-4, 11-bit, 125 kbps, secondary pins 3/11 (OBDLink FRPM rev F, 8.6, p.34)
                            val r = executeWithRetry("STP53", retries = 2)
                            if (isRejectedOrError(r)) throw BusSwitchFailedException("Bus switch failed")
                        }
                        AdapterFamily.VLINKER_FS -> {
                            // UNVERIFIED: ATHSCAN & ATMSCAN lack official manufacturer documentation
                            val r1 = executeWithRetry("ATMSCAN", retries = 2)
                            if (isRejectedOrError(r1)) throw BusSwitchFailedException("Bus switch failed")
                            val r2 = executeWithRetry("ATSPB", retries = 2)
                            if (isRejectedOrError(r2)) throw BusSwitchFailedException("Bus switch failed")
                        }
                        else -> {
                            // Standard ELM327 lacks multiplexer: notify user to flip manual DPDT switch
                            val prompt = "Flip adapter hardware switch to MS-CAN (OBD pins 3/11)"
                            onManualSwitchPrompt?.invoke(prompt)
                            val r = executeWithRetry("ATSPB", retries = 2)
                            if (isRejectedOrError(r)) throw BusSwitchFailedException("Bus switch failed")
                        }
                    }
                    currentBus = "MS-CAN"
                }
                "HS-CAN" -> {
                    when (adapterProfile.family) {
                        AdapterFamily.OBDLINK_STN -> {
                            // STP 33: ISO 15765-4, 11-bit, 500 kbps, primary pins 6/14 (OBDLink FRPM rev F, 8.6, p.34)
                            val r = executeWithRetry("STP33", retries = 2)
                            if (isRejectedOrError(r)) throw BusSwitchFailedException("Bus switch failed")
                        }
                        AdapterFamily.VLINKER_FS -> {
                            // UNVERIFIED: ATHSCAN & ATMSCAN lack official manufacturer documentation
                            val r1 = executeWithRetry("ATHSCAN", retries = 2)
                            if (isRejectedOrError(r1)) throw BusSwitchFailedException("Bus switch failed")
                            val r2 = executeWithRetry("ATSP6", retries = 2)
                            if (isRejectedOrError(r2)) throw BusSwitchFailedException("Bus switch failed")
                        }
                        else -> {
                            val prompt = "Flip adapter hardware switch to HS-CAN (OBD pins 6/14)"
                            onManualSwitchPrompt?.invoke(prompt)
                            val r = executeWithRetry("ATSP6", retries = 2)
                            if (isRejectedOrError(r)) throw BusSwitchFailedException("Bus switch failed")
                        }
                    }
                    currentBus = "HS-CAN"
                }
                else -> throw BusSwitchFailedException("Unsupported bus: $targetBus")
            }
        } catch (e: Exception) {
            if (e is BusSwitchFailedException) throw e
            throw BusSwitchFailedException("Bus switch failed")
        }
    }

    private fun isRejectedOrError(resp: String): Boolean {
        val upper = resp.trim().uppercase()
        return upper == "?" || upper.contains("ERROR") || upper.contains("UNABLE")
    }


    suspend fun setHeader(header: String) {
        val clean = header.trim().uppercase()
        if (clean == activeHeader) return
        val resp = executeWithRetry("ATSH $clean", retries = 2)
        if (cleanResponse(resp).contains("OK")) {
            activeHeader = clean
        } else {
            throw CommandFailedException("Failed to set CAN header: $clean")
        }
    }

    suspend fun executeWithRetry(command: String, retries: Int = 2, timeoutMs: Long = 3000L): String {
        var lastException: Exception? = null
        for (attempt in 0..retries) {
            try {
                traceLogger?.logTx(command)
                val raw = transport.send(command, timeoutMs)
                traceLogger?.logRx(raw)
                var cleaned = cleanResponse(raw)

                // Handle SEARCHING... prefix from auto-protocol detection
                if (cleaned.contains("SEARCHING...")) {
                    cleaned = cleaned.replace("SEARCHING...", "").trim()
                }

                // Check for adapter error strings
                checkAdapterError(cleaned)
                return cleaned
            } catch (noData: NoDataException) {
                // If ECU does not respond (NO DATA), don't waste time retrying
                traceLogger?.logRx("NO DATA")
                throw noData
            } catch (e: Exception) {
                traceLogger?.logRx("ERROR: ${e.message}")
                lastException = e
                if (attempt < retries) {
                    val backoffMs = if (e is BusBusyException) 250L else 150L
                    delay(backoffMs)
                }
            }
        }
        throw lastException ?: TimeoutException()
    }

    fun cleanResponse(raw: String): String {
        return raw.replace(">", "").trim()
    }

    fun checkAdapterError(text: String) {
        val upper = text.uppercase()
        when {
            upper == "NO DATA" || upper.startsWith("NO DATA") -> throw NoDataException()
            upper.contains("CAN ERROR") -> throw CanErrorException()
            upper.contains("BUS BUSY") -> throw BusBusyException()
            upper.contains("UNABLE TO CONNECT") -> throw UnableToConnectException()
            upper.contains("STOPPED") -> throw AdapterStoppedException()
            upper.contains("BUFFER FULL") -> throw BufferFullException()
            upper.contains("BUS INIT: ERROR") || upper == "?" -> throw CommandFailedException(text)
        }
    }

    fun isErrorResponse(text: String): Boolean {
        val upper = text.uppercase()
        return upper.contains("CAN ERROR") ||
                upper.contains("BUS INIT: ERROR") ||
                upper.contains("UNABLE TO CONNECT") ||
                upper.contains("STOPPED") ||
                upper.contains("BUS BUSY") ||
                upper.contains("BUFFER FULL") ||
                upper == "?"
    }


    private fun parseVoltage(str: String): Double {
        val cleaned = str.replace("V", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
