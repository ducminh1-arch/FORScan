package com.carscan.core

import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.transport.MockTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ELM327DriverTest {

    @Test
    fun testDriverInitSequence() = runBlocking {
        val mock = MockTransport()
        val driver = ELM327Driver(mock)

        mock.connect()
        driver.initialize()

        assertTrue(driver.isInitialized)
        assertTrue(driver.adapterVersion.contains("ELM327"))
        assertEquals(12.6, driver.batteryVoltage, 0.1)
    }

    @Test
    fun testSetHeader() = runBlocking {
        val mock = MockTransport()
        val driver = ELM327Driver(mock)

        mock.connect()
        driver.initialize()
        driver.setHeader("726")

        assertEquals("726", driver.activeHeader)
        assertEquals("726", mock.currentHeader)
    }

    @Test
    fun testCleanResponse() {
        val driver = ELM327Driver(MockTransport())
        val dirty = "  OK\r\n\r\n>  "
        assertEquals("OK", driver.cleanResponse(dirty))
    }

    @Test
    fun testErrorDetection() {
        val driver = ELM327Driver(MockTransport())
        assertTrue(driver.isErrorResponse("CAN ERROR"))
        assertTrue(driver.isErrorResponse("BUS INIT: ERROR"))
        assertTrue(driver.isErrorResponse("?"))
        assertFalse(driver.isErrorResponse("41 0C 0D 48"))
    }
}
