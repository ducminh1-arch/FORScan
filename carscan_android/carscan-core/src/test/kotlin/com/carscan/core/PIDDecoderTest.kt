package com.carscan.core

import com.carscan.core.models.PIDDefinition
import com.carscan.core.protocol.PIDDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

class PIDDecoderTest {

    private val decoder = PIDDecoder()

    @Test
    fun testRPMDecoding() {
        val rpmPid = PIDDefinition(
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
            description = ""
        )
        // 0x0D 0x48 = (13 * 256 + 72) / 4 = 3400 / 4 = 850 RPM
        val data = byteArrayOf(0x0D.toByte(), 0x48.toByte())
        val result = decoder.decode(rpmPid, data)
        assertEquals(850.0, result ?: 0.0, 0.01)
    }

    @Test
    fun testSpeedDecoding() {
        val spdPid = PIDDefinition(
            id = "PID_010D",
            mode = "01",
            pid = "0D",
            name = "Vehicle Speed",
            shortName = "VSS",
            category = "Speed",
            module = "PCM",
            byteCount = 1,
            formula = "A",
            unit = "km/h",
            imperialUnit = "mph",
            min = 0.0,
            max = 255.0,
            precision = 0,
            description = ""
        )
        val data = byteArrayOf(100.toByte())
        val metric = decoder.decode(spdPid, data, imperial = false)
        assertEquals(100.0, metric ?: 0.0, 0.01)

        val imperial = decoder.decode(spdPid, data, imperial = true)
        assertEquals(62.1371, imperial ?: 0.0, 0.01)
    }

    @Test
    fun testCoolantTempDecoding() {
        val ectPid = PIDDefinition(
            id = "PID_0105",
            mode = "01",
            pid = "05",
            name = "Engine Coolant Temperature",
            shortName = "ECT",
            category = "Temperature",
            module = "PCM",
            byteCount = 1,
            formula = "A - 40.0",
            unit = "°C",
            imperialUnit = "°F",
            min = -40.0,
            max = 215.0,
            precision = 1,
            description = ""
        )
        // 130 - 40 = 90°C
        val data = byteArrayOf(130.toByte())
        val metric = decoder.decode(ectPid, data, imperial = false)
        assertEquals(90.0, metric ?: 0.0, 0.01)

        // 90°C = 194°F
        val imperial = decoder.decode(ectPid, data, imperial = true)
        assertEquals(194.0, imperial ?: 0.0, 0.01)
    }

    @Test
    fun testVoltageDecoding() {
        val vpwrPid = PIDDefinition(
            id = "PID_0142",
            mode = "01",
            pid = "42",
            name = "Control Module Voltage",
            shortName = "VPWR",
            category = "Electrical",
            module = "PCM",
            byteCount = 2,
            formula = "((A * 256) + B) / 1000.0",
            unit = "V",
            min = 0.0,
            max = 20.0,
            precision = 2,
            description = ""
        )
        // 0x35 0xE8 = 13800 mV = 13.8V
        val data = byteArrayOf(0x35.toByte(), 0xE8.toByte())
        val result = decoder.decode(vpwrPid, data)
        assertEquals(13.8, result ?: 0.0, 0.01)
    }

    @Test
    fun testDTCDecoding() {
        // High 2 bits: 00=P, 01=C, 10=B, 11=U
        // Next 2 bits: 00=0, 01=1, 10=2, 11=3
        // Next 4 bits: digit 2
        // Byte 1: digits 3 and 4

        // User case 1: [0x91, 0xD9] -> B11D9
        // 0x91 = 10 01 0001 (B, 1, 1)
        // 0xD9 = D, 9
        val dtcB11D9 = PIDDecoder.decodeDTC(byteArrayOf(0x91.toByte(), 0xD9.toByte()))
        assertEquals("B11D9", dtcB11D9)

        // User case 2: [0xB1, 0x1D] -> B311D
        // 0xB1 = 10 11 0001 (B, 3, 1)
        // 0x1D = 1, D
        val dtcB311D = PIDDecoder.decodeDTC(byteArrayOf(0xB1.toByte(), 0x1D.toByte()))
        assertEquals("B311D", dtcB311D)

        // Standard OBD-II Powertrain codes
        assertEquals("P0171", PIDDecoder.decodeDTC(byteArrayOf(0x01.toByte(), 0x71.toByte())))
        assertEquals("P0300", PIDDecoder.decodeDTC(byteArrayOf(0x03.toByte(), 0x00.toByte())))

        // Chassis code: C0031
        assertEquals("C0031", PIDDecoder.decodeDTC(byteArrayOf(0x40.toByte(), 0x31.toByte())))

        // Network code: U0100
        assertEquals("U0100", PIDDecoder.decodeDTC(byteArrayOf(0xC1.toByte(), 0x00.toByte())))
    }
}

