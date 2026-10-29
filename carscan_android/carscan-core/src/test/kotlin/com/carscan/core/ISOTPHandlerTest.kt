package com.carscan.core

import com.carscan.core.protocol.ISOTPHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class ISOTPHandlerTest {

    @Test
    fun testReassembleSpacedVINMultiFrame() {
        val handler = ISOTPHandler()
        val sampleLog = """
            7E8 10 14 49 02 01 31 46 41
            7E8 21 36 50 38 43 46 35 48
            7E8 22 35 31 32 33 34 35 36
            >
        """.trimIndent()

        val messages = handler.reassemble(sampleLog)
        assertEquals(1, messages.size)

        val msg = messages[0]
        assertEquals("7E8", msg.canId)
        assertEquals(20, msg.payload.size)

        // Check Mode 09 positive response bytes: 0x49 0x02 0x01
        assertEquals(0x49.toByte(), msg.payload[0])
        assertEquals(0x02.toByte(), msg.payload[1])
        assertEquals(0x01.toByte(), msg.payload[2])

        // Verify VIN ASCII string
        val vinBytes = msg.payload.sliceArray(3 until 20)
        val vinStr = String(vinBytes, Charsets.US_ASCII)
        assertEquals("1FA6P8CF5H5123456", vinStr)
    }

    @Test
    fun testReassembleUnspacedVINMultiFrame() {
        val handler = ISOTPHandler()
        val sampleLog = """
            7E81014490201314641
            7E82136503843463548
            7E82235313233343536
            >
        """.trimIndent()

        val messages = handler.reassemble(sampleLog)
        assertEquals(1, messages.size)

        val msg = messages[0]
        assertEquals("7E8", msg.canId)
        assertEquals(20, msg.payload.size)

        val vinBytes = msg.payload.sliceArray(3 until 20)
        val vinStr = String(vinBytes, Charsets.US_ASCII)
        assertEquals("1FA6P8CF5H5123456", vinStr)
    }

    @Test
    fun testSingleFrame() {
        val handler = ISOTPHandler()
        val sampleLog = "7E8 04 41 0C 0D 48\r\r>"
        val messages = handler.reassemble(sampleLog)
        assertEquals(1, messages.size)
        assertEquals("7E8", messages[0].canId)
        assertEquals(4, messages[0].payload.size)
        assertEquals(0x41.toByte(), messages[0].payload[0])
        assertEquals(0x0C.toByte(), messages[0].payload[1])
    }
}
