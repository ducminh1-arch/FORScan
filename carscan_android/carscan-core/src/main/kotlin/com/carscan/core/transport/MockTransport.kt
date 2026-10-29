package com.carscan.core.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class MockTransport(
    var simulatedVin: String = "1FA6P8CF5M5123456", // 10th char 'M' = Model Year 2021 per ISO 3779
    var simulatedVoltage: Double = 12.6,
    var simulatedLatencyMs: Long = 20L
) : Transport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val mutex = Mutex()
    var headersEnabled: Boolean = false
    var spacesEnabled: Boolean = true
    var linefeedsEnabled: Boolean = true
    var currentHeader: String = "7E0"
    var simulatedRpm: Double = 850.0
    var simulatedSpeed: Double = 45.0
    var simulatedEct: Double = 90.0

    val simulatedDTCs = mutableMapOf(
        "7E0" to mutableListOf("P0171", "P0300"),
        "726" to mutableListOf("B11D909"),
        "760" to mutableListOf<String>(),
        "7D0" to mutableListOf<String>(),
        "7E1" to mutableListOf<String>()
    )

    override suspend fun connect() {
        mutex.withLock {
            _state.value = ConnectionState.Connecting
            if (simulatedLatencyMs > 0) delay(simulatedLatencyMs)
            _state.value = ConnectionState.Connected
        }
    }

    override fun disconnect() {
        _state.value = ConnectionState.Disconnected
    }

    val sentCommands = mutableListOf<String>()
    var simulatedStdi: String = "OBDLink EX"

    override suspend fun send(command: String, timeoutMs: Long): String {
        mutex.withLock {
            if (_state.value !is ConnectionState.Connected) {
                throw NotConnectedException()
            }

            if (simulatedLatencyMs > 0) delay(simulatedLatencyMs)

            val clean = command.trim().uppercase().replace(" ", "")
            sentCommands.add(clean)

            if (clean.startsWith("AT") || clean.startsWith("ST")) {
                return handleATCommand(clean)
            }
            return handleOBDCommand(clean)
        }
    }

    var simulateNRC78: Boolean = false
    var forcedError: String? = null
    var isSTNAdapter: Boolean = false
    var isVlinkerAdapter: Boolean = false
    var rejectBusSwitch: Boolean = false
    val unresponsiveHeaders = mutableSetOf("736", "754")

    private fun handleATCommand(cmd: String): String {
        if (rejectBusSwitch && (cmd.startsWith("STP") || cmd == "ATHSCAN" || cmd == "ATMSCAN" || cmd == "ATSPB")) {
            return "?\r\r>"
        }
        return when {
            cmd == "ATZ" || cmd == "ATWS" -> {
                headersEnabled = false
                spacesEnabled = true
                linefeedsEnabled = true
                currentHeader = "7E0"
                "\r\rELM327 v1.5\r\r>"
            }
            cmd == "ATE0" || cmd == "ATE1" -> "OK\r\r>"
            cmd == "ATL0" -> { linefeedsEnabled = false; "OK\r\r>" }
            cmd == "ATL1" -> { linefeedsEnabled = true; "OK\r\r>" }
            cmd == "ATS0" -> { spacesEnabled = false; "OK\r\r>" }
            cmd == "ATS1" -> { spacesEnabled = true; "OK\r\r>" }
            cmd == "ATH0" -> { headersEnabled = false; "OK\r\r>" }
            cmd == "ATH1" -> { headersEnabled = true; "OK\r\r>" }
            cmd.startsWith("ATSP") -> "OK\r\r>"
            cmd == "ATDP" -> "ISO 15765-4 (CAN 11/500)\r\r>"
            cmd == "ATRV" -> String.format("%.1fV\r\r>", simulatedVoltage)
            cmd.startsWith("ATSH") -> {
                currentHeader = cmd.substring(4)
                "OK\r\r>"
            }
            cmd.startsWith("ATCRA") || cmd.startsWith("ATPB") || cmd.startsWith("ATST") -> "OK\r\r>"
            cmd == "ATI" -> {
                if (isVlinkerAdapter) "vLinker FS v2.2\r\r>" else "ELM327 v1.5\r\r>"
            }
            cmd == "STI" -> {
                if (isSTNAdapter) "STN2120 v5.1.0\r\r>" else "?\r\r>"
            }
            cmd == "STDI" -> {
                if (isSTNAdapter) "$simulatedStdi\r\r>" else "?\r\r>"
            }
            cmd.startsWith("STP") -> "OK\r\r>"
            cmd == "ATHSCAN" || cmd == "ATMSCAN" -> "OK\r\r>"
            else -> "OK\r\r>"
        }
    }

    private fun handleOBDCommand(cmd: String): String {
        val err = forcedError
        if (err != null) {
            return "$err\r\r>"
        }

        if (unresponsiveHeaders.contains(currentHeader)) {
            return "NO DATA\r\r>"
        }

        val rxId = when (currentHeader) {
            "7E0" -> "7E8"
            "7E1" -> "7E9"
            "726" -> "72E"
            "760" -> "768"
            "7D0" -> "7D8"
            "720" -> "728"
            "737" -> "73F"
            else -> "7E8"
        }

        return when {
            cmd.startsWith("01") -> {
                val rawPids = cmd.substring(2)
                if (rawPids.length == 2) {
                    handleMode01(rxId, rawPids)
                } else if (rawPids.length > 2 && rawPids.length % 2 == 0) {
                    val pids = (0 until rawPids.length step 2).map { rawPids.substring(it, it + 2) }
                    handleMode01Multi(rxId, pids)
                } else {
                    "NO DATA\r\r>"
                }
            }
            cmd == "03" -> handleMode03(rxId, currentHeader)
            cmd == "04" -> {
                simulatedDTCs[currentHeader]?.clear()
                formatSingleFrame(rxId, listOf("44"))
            }
            cmd.startsWith("14") -> {
                simulatedDTCs[currentHeader]?.clear()
                if (simulateNRC78) {
                    val eol = "\r"
                    val nrcLine = if (headersEnabled) formatLine(rxId, listOf("03", "7F", "14", "78")) else "7F 14 78"
                    val okLine = if (headersEnabled) formatLine(rxId, listOf("01", "54")) else "54"
                    "$nrcLine$eol$okLine\r\r>"
                } else {
                    formatSingleFrame(rxId, listOf("54"))
                }
            }
            cmd == "07" -> formatSingleFrame(rxId, listOf("47", "00"))
            cmd == "0A" -> formatSingleFrame(rxId, listOf("4A", "00"))
            cmd.startsWith("09") -> {
                val pid = cmd.substring(2).take(2)
                handleMode09(rxId, pid)
            }
            cmd.startsWith("22") -> {
                val did = cmd.substring(2)
                handleMode22(rxId, did)
            }
            cmd.startsWith("19") -> handleMode19(rxId, currentHeader)
            cmd.startsWith("31") -> {
                val sub = if (cmd.length >= 4) cmd.substring(2, 4) else "01"
                val r1 = if (cmd.length >= 6) cmd.substring(4, 6) else "00"
                val r2 = if (cmd.length >= 8) cmd.substring(6, 8) else "00"
                formatSingleFrame(rxId, listOf("71", sub, r1, r2, "00"))
            }
            cmd.startsWith("11") -> {
                val sub = if (cmd.length >= 4) cmd.substring(2, 4) else "01"
                formatSingleFrame(rxId, listOf("51", sub))
            }
            cmd.startsWith("3E") -> formatSingleFrame(rxId, listOf("7E", "00"))
            cmd.startsWith("10") -> {
                val sub = if (cmd.length >= 4) cmd.substring(2, 4) else "01"
                formatSingleFrame(rxId, listOf("50", sub, "00", "32", "01", "F4"))
            }
            else -> "NO DATA\r\r>"
        }
    }


    private fun getPidDataTokens(pid: String): List<String>? {
        return when (pid) {
            "00" -> listOf("BE", "3F", "B8", "13")
            "20" -> listOf("80", "07", "20", "01")
            "40" -> listOf("7E", "D0", "00", "00")
            "60" -> listOf("00", "00", "00", "00")
            "0C" -> {
                val jitter = Random.nextDouble(-15.0, 15.0)
                val rpm = maxOf(600.0, simulatedRpm + jitter)
                val raw = (rpm * 4.0).toInt()
                listOf(String.format("%02X", (raw shr 8) and 0xFF), String.format("%02X", raw and 0xFF))
            }
            "0D" -> listOf(String.format("%02X", simulatedSpeed.toInt()))
            "05" -> listOf(String.format("%02X", (simulatedEct + 40.0).toInt()))
            "0F" -> listOf(String.format("%02X", (28.0 + 40.0).toInt()))
            "0B" -> listOf("23")
            "10" -> {
                val valRaw = (4.5 * 100.0).toInt()
                listOf(String.format("%02X", (valRaw shr 8) and 0xFF), String.format("%02X", valRaw and 0xFF))
            }
            "11" -> listOf("26")
            "04" -> listOf("38")
            "2F" -> listOf("AD")
            "42" -> {
                val rawV = (simulatedVoltage * 1000.0).toInt()
                listOf(String.format("%02X", (rawV shr 8) and 0xFF), String.format("%02X", rawV and 0xFF))
            }
            "46" -> listOf("41")
            else -> null
        }
    }

    private fun handleMode01(rxId: String, pid: String): String {
        if (pid == "00" && currentHeader == "7DF") {
            val eol = "\r"
            val l1 = if (headersEnabled) formatLine("7E8", listOf("06", "41", "00", "BE", "3F", "B8", "13")) else "41 00 BE 3F B8 13"
            val l2 = if (headersEnabled) formatLine("7E9", listOf("06", "41", "00", "80", "00", "00", "01")) else "41 00 80 00 00 01"
            return "$l1$eol$l2$eol>"
        }

        val tokens = getPidDataTokens(pid)
        return if (tokens != null) {
            formatSingleFrame(rxId, listOf("41", pid) + tokens)
        } else {
            "NO DATA\r\r>"
        }
    }

    var multiPidRejectMessage: String? = null

    private fun handleMode01Multi(rxId: String, pids: List<String>): String {
        val reject = multiPidRejectMessage
        if (reject != null && pids.size > 1) {
            return "$reject\r\r>"
        }

        val combined = mutableListOf("41")
        for (p in pids) {
            val data = getPidDataTokens(p)
            if (data != null) {
                combined.add(p)
                combined.addAll(data)
            }
        }
        return if (combined.size > 1) {
            formatSingleFrame(rxId, combined)
        } else {
            "NO DATA\r\r>"
        }
    }

    private fun handleMode09(rxId: String, pid: String): String {
        if (pid == "02") {
            val asciiBytes = simulatedVin.toByteArray(Charsets.US_ASCII).map { String.format("%02X", it) }
            val payload = listOf("49", "02", "01") + asciiBytes
            val totalLen = payload.size

            val ff = listOf("10", String.format("%02X", totalLen)) + payload.subList(0, 6)
            val cf1 = listOf("21") + payload.subList(6, minOf(13, totalLen))
            val cf2 = listOf("22") + payload.subList(13, minOf(20, totalLen))

            val eol = "\r"
            return if (headersEnabled) {
                val l1 = formatLine(rxId, ff)
                val l2 = formatLine(rxId, cf1)
                val l3 = formatLine(rxId, cf2)
                "$l1$eol$l2$eol$l3$eol>"
            } else {
                val l0 = String.format("0%02X", totalLen)
                val l1 = "0: " + payload.subList(0, 6).joinToString(" ")
                val l2 = "1: " + payload.subList(6, 13).joinToString(" ")
                val l3 = "2: " + payload.subList(13, 20).joinToString(" ")
                "$l0$eol$l1$eol$l2$eol$l3$eol>"
            }
        }
        return "NO DATA\r\r>"
    }

    private fun handleMode03(rxId: String, txId: String): String {
        val codes = simulatedDTCs[txId] ?: emptyList<String>()
        if (codes.isEmpty()) {
            return formatSingleFrame(rxId, listOf("43", "00"))
        }
        val tokens = mutableListOf("43", String.format("%02X", codes.size))
        for (code in codes.take(3)) {
            val typeBits = when (code.firstOrNull() ?: 'P') {
                'P' -> 0
                'C' -> 1
                'B' -> 2
                else -> 3
            }
            val d1 = code.drop(1).take(1).toIntOrNull() ?: 0
            val d2 = code.drop(2).take(1).toIntOrNull(16) ?: 0
            val b0 = (typeBits shl 6) or (d1 shl 4) or d2
            val b1 = code.drop(3).take(2).toIntOrNull(16) ?: 0
            tokens.add(String.format("%02X", b0))
            tokens.add(String.format("%02X", b1))
        }
        return formatSingleFrame(rxId, tokens)
    }

    private fun handleMode22(rxId: String, did: String): String {
        return when (did) {
            "028C" -> formatSingleFrame(rxId, listOf("62", "02", "8C", "02", "1C"))
            "1E1C" -> formatSingleFrame(rxId, listOf("62", "1E", "1C", "07", "A0"))
            "0367" -> formatSingleFrame(rxId, listOf("62", "03", "67", "55"))
            else -> "NO DATA\r\r>"
        }
    }

    private fun handleMode19(rxId: String, txId: String): String {
        val codes = simulatedDTCs[txId] ?: emptyList<String>()
        val baseResponse = if (codes.isEmpty()) {
            formatSingleFrame(rxId, listOf("59", "02", "FF"))
        } else {
            val tokens = mutableListOf("59", "02", "FF")
            for (c in codes) {
                when {
                    c.startsWith("B11D9") -> tokens.addAll(listOf("91", "D9", "09", "2F"))
                    c.startsWith("B311D") -> tokens.addAll(listOf("B1", "1D", "09", "2F"))
                    c == "P0171" -> tokens.addAll(listOf("01", "71", "00", "2F"))
                    c == "P0300" -> tokens.addAll(listOf("03", "00", "00", "2F"))
                }
            }
            formatSingleFrame(rxId, tokens)
        }

        return if (simulateNRC78) {
            val eol = "\r"
            val nrcLine = if (headersEnabled) formatLine(rxId, listOf("03", "7F", "19", "78")) else "7F 19 78"
            "$nrcLine$eol$baseResponse"
        } else {
            baseResponse
        }
    }

    private fun formatSingleFrame(rxId: String, tokens: List<String>): String {
        val lenStr = String.format("%02X", tokens.size)
        val sfTokens = listOf(lenStr) + tokens
        val line = if (headersEnabled) formatLine(rxId, sfTokens) else formatDataOnly(tokens)
        return "$line\r\r>"
    }

    private fun formatLine(header: String, tokens: List<String>): String {
        return if (spacesEnabled) "$header " + tokens.joinToString(" ") else "$header" + tokens.joinToString("")
    }

    private fun formatDataOnly(tokens: List<String>): String {
        return if (spacesEnabled) tokens.joinToString(" ") else tokens.joinToString("")
    }
}
