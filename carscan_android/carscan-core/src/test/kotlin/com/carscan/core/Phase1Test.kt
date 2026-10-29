package com.carscan.core

import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.models.PIDDefinition
import com.carscan.core.protocol.OBD2Service
import com.carscan.core.protocol.SupportedPidsDetector
import com.carscan.core.transport.MockTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1Test {

    @Test
    fun testSupportedPidsBitmaskParsing() {
        val detector = SupportedPidsDetector()
        // Bitmask for 01-20: BE 3F B8 13
        val bytes = byteArrayOf(0xBE.toByte(), 0x3F.toByte(), 0xB8.toByte(), 0x13.toByte())
        val result = detector.parseBitmask(bytes, 0)

        assertTrue(result.hasNextPage)
        assertTrue(result.supportedPids.contains("0C")) // RPM
        assertTrue(result.supportedPids.contains("0D")) // Speed
        assertTrue(result.supportedPids.contains("05")) // ECT
        assertTrue(result.supportedPids.contains("11")) // Throttle
    }

    @Test
    fun testECUDiscovery() = runBlocking {
        val mock = MockTransport()
        val driver = ELM327Driver(mock)
        val obd = OBD2Service(driver)

        mock.connect()
        driver.initialize()

        val ecus = obd.discoverECUs()
        assertTrue(ecus.contains("7E8")) // PCM
        assertTrue(ecus.contains("7E9")) // TCM
    }

    @Test
    fun testMultiPidBatchQuery() = runBlocking {
        val mock = MockTransport()
        val driver = ELM327Driver(mock)
        val obd = OBD2Service(driver)

        mock.connect()
        driver.initialize()

        val rpmPid = PIDDefinition(id = "PID_010C", mode = "01", pid = "0C", name = "RPM", shortName = "RPM", category = "Engine", module = "PCM", byteCount = 2, formula = "((A * 256) + B) / 4.0", unit = "RPM", min = 0.0, max = 8000.0, precision = 0, description = "")
        val spdPid = PIDDefinition(id = "PID_010D", mode = "01", pid = "0D", name = "Speed", shortName = "Speed", category = "Speed", module = "PCM", byteCount = 1, formula = "A", unit = "km/h", imperialUnit = "mph", min = 0.0, max = 255.0, precision = 0, description = "")
        val ectPid = PIDDefinition(id = "PID_0105", mode = "01", pid = "05", name = "ECT", shortName = "ECT", category = "Temperature", module = "PCM", byteCount = 1, formula = "A - 40.0", unit = "°C", imperialUnit = "°F", min = -40.0, max = 215.0, precision = 1, description = "")

        val batch = obd.readPIDsBatch(listOf(rpmPid, spdPid, ectPid))
        assertNotNull(batch["PID_010C"])
        assertNotNull(batch["PID_010D"])
        assertNotNull(batch["PID_0105"])

        assertEquals(45.0, batch["PID_010D"]?.numericValue ?: 0.0, 0.01)
        assertEquals(90.0, batch["PID_0105"]?.numericValue ?: 0.0, 0.01)
    }

    @Test
    fun testMode03AndMode07DTCs() = runBlocking {
        val mock = MockTransport()
        val driver = ELM327Driver(mock)
        val obd = OBD2Service(driver)

        mock.connect()
        driver.initialize()

        val stored = obd.readStoredDTCs()
        assertEquals(2, stored.size)
        assertEquals("P0171", stored[0].code)
        assertEquals("P0300", stored[1].code)

        val pending = obd.readPendingDTCs()
        assertEquals(0, pending.size)
    }

    @Test
    fun testAdaptiveFallbackWhenAdapterReturnsQuestionMark() = runBlocking {
        val mock = MockTransport()
        mock.multiPidRejectMessage = "?"
        val driver = ELM327Driver(mock)
        val obd = OBD2Service(driver)

        mock.connect()
        driver.initialize()

        val rpmPid = PIDDefinition(id = "PID_010C", mode = "01", pid = "0C", name = "RPM", shortName = "RPM", category = "Engine", module = "PCM", byteCount = 2, formula = "((A * 256) + B) / 4.0", unit = "RPM", min = 0.0, max = 8000.0, precision = 0, description = "")
        val spdPid = PIDDefinition(id = "PID_010D", mode = "01", pid = "0D", name = "Speed", shortName = "Speed", category = "Speed", module = "PCM", byteCount = 1, formula = "A", unit = "km/h", imperialUnit = "mph", min = 0.0, max = 255.0, precision = 0, description = "")

        assertEquals(6, obd.maxBatchSize)

        // Reading batch should detect "?" rejection, decrease maxBatchSize to 1, and fall back to single PID queries
        val batch = obd.readPIDsBatch(listOf(rpmPid, spdPid))
        assertEquals(1, obd.maxBatchSize)
        assertNotNull(batch["PID_010C"])
        assertNotNull(batch["PID_010D"])
        assertEquals(45.0, batch["PID_010D"]?.numericValue ?: 0.0, 0.01)
    }

    @Test
    fun testAdaptiveFallbackWhenAdapterReturnsBufferFull() = runBlocking {
        val mock = MockTransport()
        mock.multiPidRejectMessage = "BUFFER FULL"
        val driver = ELM327Driver(mock)
        val obd = OBD2Service(driver)

        mock.connect()
        driver.initialize()

        val spdPid = PIDDefinition(id = "PID_010D", mode = "01", pid = "0D", name = "Speed", shortName = "Speed", category = "Speed", module = "PCM", byteCount = 1, formula = "A", unit = "km/h", imperialUnit = "mph", min = 0.0, max = 255.0, precision = 0, description = "")
        val ectPid = PIDDefinition(id = "PID_0105", mode = "01", pid = "05", name = "ECT", shortName = "ECT", category = "Temperature", module = "PCM", byteCount = 1, formula = "A - 40.0", unit = "°C", imperialUnit = "°F", min = -40.0, max = 215.0, precision = 1, description = "")

        val batch = obd.readPIDsBatch(listOf(spdPid, ectPid))
        assertEquals(1, obd.maxBatchSize)
        assertNotNull(batch["PID_010D"])
        assertNotNull(batch["PID_0105"])
        assertEquals(90.0, batch["PID_0105"]?.numericValue ?: 0.0, 0.01)
    }
}

