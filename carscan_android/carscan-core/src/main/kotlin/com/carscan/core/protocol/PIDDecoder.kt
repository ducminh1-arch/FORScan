package com.carscan.core.protocol

import com.carscan.core.models.PIDDefinition

class PIDDecoder {

    fun decode(pid: PIDDefinition, dataBytes: ByteArray, imperial: Boolean = false): Double? {
        if (dataBytes.isEmpty()) return null

        val a = dataBytes[0].toInt() and 0xFF
        val b = if (dataBytes.size > 1) (dataBytes[1].toInt() and 0xFF) else 0

        val metricValue: Double = when (pid.id) {
            "PID_010C" -> ((a * 256.0) + b) / 4.0 // RPM
            "PID_010D" -> a.toDouble() // Speed km/h
            "PID_0105", "PID_010F", "PID_0146" -> a - 40.0 // Temps °C
            "PID_010B" -> a.toDouble() // MAP kPa
            "PID_0110" -> ((a * 256.0) + b) / 100.0 // MAF g/s
            "PID_0111", "PID_0104", "PID_012F" -> (a * 100.0) / 255.0 // Percentages
            "PID_0142" -> ((a * 256.0) + b) / 1000.0 // Voltage V
            "PID_FORD_22028C" -> (((a * 256.0) + b) / 4.0) - 40.0 // CHT
            "PID_FORD_221E1C" -> (((a * 256.0) + b) / 16.0) - 40.0 // TFT
            "PID_FORD_220367" -> a.toDouble() // SOC %
            else -> a.toDouble()
        }

        return if (imperial) {
            when (pid.id) {
                "PID_010D" -> metricValue * 0.621371
                "PID_0105", "PID_010F", "PID_0146", "PID_FORD_22028C", "PID_FORD_221E1C" -> (metricValue * 1.8) + 32.0
                "PID_010B" -> metricValue * 0.145038
                "PID_0110" -> metricValue * 0.132277
                else -> metricValue
            }
        } else {
            metricValue
        }
    }

    companion object {
        fun decodeDTC(b0: Int, b1: Int): String {
            val typeBits = (b0 shr 6) and 0x03
            val prefix = when (typeBits) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val d1 = (b0 shr 4) and 0x03
            val d2 = b0 and 0x0F
            val d3 = (b1 shr 4) and 0x0F
            val d4 = b1 and 0x0F
            return String.format("%s%X%X%X%X", prefix, d1, d2, d3, d4)
        }

        fun decodeDTC(bytes: ByteArray): String {
            require(bytes.size >= 2) { "DTC requires at least 2 bytes" }
            return decodeDTC(bytes[0].toInt() and 0xFF, bytes[1].toInt() and 0xFF)
        }
    }
}

