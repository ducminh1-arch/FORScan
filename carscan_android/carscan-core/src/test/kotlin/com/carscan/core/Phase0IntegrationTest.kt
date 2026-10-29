package com.carscan.core

import com.carscan.core.spike.Phase0Spike
import com.carscan.core.transport.MockTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase0IntegrationTest {

    @Test
    fun testPhase0SpikeWithMockTransport() = runBlocking {
        val mock = MockTransport()
        mock.simulatedLatencyMs = 1L

        val spike = Phase0Spike()
        val report = spike.run(mock)

        assertTrue(report.isSuccess)
        assertEquals("1FA6P8CF5M5123456", report.vin)
        assertEquals(2021, com.carscan.core.protocol.VINDecoder.decodeModelYear(report.vin))
        assertEquals(12.6, report.batteryVoltage, 0.1)
        assertTrue(report.sampleRpm in 700.0..1000.0)
        assertEquals(2, report.dtcCount) // P0171, P0300
    }
}
