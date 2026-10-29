package com.carscan.core.protocol

class SupportedPidsDetector {

    data class DetectionResult(
        val supportedPids: Set<String>,
        val hasNextPage: Boolean
    )

    fun parseBitmask(bytes: ByteArray, basePid: Int): DetectionResult {
        if (bytes.size < 4) return DetectionResult(emptySet(), false)
        val result = mutableSetOf<String>()

        for (byteIdx in 0 until 4) {
            val byteVal = bytes[byteIdx].toInt() and 0xFF
            for (bitIdx in 0 until 8) {
                val mask = 1 shl (7 - bitIdx)
                if ((byteVal and mask) != 0) {
                    val pidNum = basePid + (byteIdx * 8) + (bitIdx + 1)
                    val pidHex = String.format("%02X", pidNum)
                    result.add(pidHex)
                }
            }
        }

        val nextPagePid = basePid + 32
        val nextPageHex = String.format("%02X", nextPagePid)
        val hasNext = result.contains(nextPageHex)

        return DetectionResult(result, hasNext)
    }
}
