package com.carscan.core.protocol

enum class ISOTPFrameType(val nibble: Int) {
    SINGLE(0),
    FIRST(1),
    CONSECUTIVE(2),
    FLOW_CONTROL(3);

    companion object {
        fun fromNibble(n: Int): ISOTPFrameType? = entries.find { it.nibble == n }
    }
}

data class ISOTPMessage(
    val canId: String?,
    val payload: ByteArray
) {
    val asciiString: String
        get() = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ISOTPMessage
        if (canId != other.canId) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = canId?.hashCode() ?: 0
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

class ISOTPHandler {

    fun reassemble(response: String): List<ISOTPMessage> {
        val lines = response.replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(">") }

        val completed = mutableListOf<ISOTPMessage>()
        var detectedCanId: String? = null
        var totalLength = 0
        val accumulatedPayload = mutableListOf<Byte>()

        for (line in lines) {
            var lineCanId: String? = null
            val tokens = tokenizeHexLine(line) { id -> lineCanId = id }
            if (tokens.isEmpty()) continue
            if (lineCanId != null) detectedCanId = lineCanId

            val b0 = tokens[0].toIntOrNull(16) ?: continue
            val frameType = ISOTPFrameType.fromNibble((b0 shr 4) and 0x0F) ?: continue

            when (frameType) {
                ISOTPFrameType.SINGLE -> {
                    val len = b0 and 0x0F
                    val bytes = tokens.drop(1).take(len).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                    completed.add(ISOTPMessage(detectedCanId, bytes))
                }
                ISOTPFrameType.FIRST -> {
                    if (tokens.size < 2) continue
                    val b1 = tokens[1].toIntOrNull(16) ?: continue
                    totalLength = ((b0 and 0x0F) shl 8) or b1
                    val bytes = tokens.drop(2).mapNotNull { it.toIntOrNull(16)?.toByte() }
                    accumulatedPayload.clear()
                    accumulatedPayload.addAll(bytes)
                }
                ISOTPFrameType.CONSECUTIVE -> {
                    val bytes = tokens.drop(1).mapNotNull { it.toIntOrNull(16)?.toByte() }
                    accumulatedPayload.addAll(bytes)
                    if (totalLength > 0 && accumulatedPayload.size >= totalLength) {
                        completed.add(ISOTPMessage(detectedCanId, accumulatedPayload.take(totalLength).toByteArray()))
                        accumulatedPayload.clear()
                        totalLength = 0
                    }
                }
                ISOTPFrameType.FLOW_CONTROL -> {}
            }
        }

        if (accumulatedPayload.isNotEmpty()) {
            val len = if (totalLength > 0) minOf(totalLength, accumulatedPayload.size) else accumulatedPayload.size
            completed.add(ISOTPMessage(detectedCanId, accumulatedPayload.take(len).toByteArray()))
        }

        return completed
    }

    fun tokenizeHexLine(line: String, onCanIdFound: (String) -> Unit): List<String> {
        var clean = line.trim()
        if (clean.contains(" ")) {
            val parts = clean.split(" ").filter { it.isNotEmpty() }.toMutableList()
            if (parts.isNotEmpty() && (parts[0].length == 3 || parts[0].length == 8)) {
                onCanIdFound(parts[0])
                parts.removeAt(0)
            }
            return parts
        } else {
            if (clean.length >= 5) {
                val first3 = clean.take(3)
                if (first3.toIntOrNull(16) != null) {
                    onCanIdFound(first3)
                    clean = clean.drop(3)
                }
            }
            val tokens = mutableListOf<String>()
            var idx = 0
            while (idx < clean.length) {
                val nextIdx = minOf(idx + 2, clean.length)
                tokens.add(clean.substring(idx, nextIdx))
                idx = nextIdx
            }
            return tokens
        }
    }
}
