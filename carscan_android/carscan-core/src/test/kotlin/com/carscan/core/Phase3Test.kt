package com.carscan.core

import com.carscan.core.logging.CSVLogRecord
import com.carscan.core.logging.CSVLogService
import com.carscan.core.logging.RawTraceLogger
import com.carscan.core.logging.TraceDirection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Phase3Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCSVHeaderAndRecordFormatting() {
        val outDir = tempFolder.newFolder("csv_test")
        val logger = CSVLogService()
        val file = logger.startLogging(outDir, "session_test")

        assertTrue(file.exists())
        assertEquals("session_test.csv", file.name)

        // Log sample PID records
        logger.logPID("0C", "Engine RPM", 750.5, "RPM", 1700000000000L)
        logger.logPID("0D", "Vehicle Speed, Primary", 45.0, "km/h", 1700000001000L)
        logger.stopLogging()

        // Read and verify records
        val records = logger.readLogRecords(file)
        assertEquals(2, records.size)

        // Record 1 verification
        assertEquals("0C", records[0].pid)
        assertEquals("Engine RPM", records[0].name)
        assertEquals(750.5, records[0].value, 0.001)
        assertEquals("RPM", records[0].unit)
        assertTrue(records[0].timestamp.contains("T"))

        // Record 2 verification: name with comma was properly quoted/parsed
        assertEquals("0D", records[1].pid)
        assertEquals("Vehicle Speed, Primary", records[1].name)
        assertEquals(45.0, records[1].value, 0.001)
        assertEquals("km/h", records[1].unit)

        // Verify CSV header line
        val firstLine = file.readLines().first()
        assertEquals("timestamp,pid,name,value,unit", firstLine)
    }

    @Test
    fun testCSVFileSizeLimitEnforcementAndRotation() {
        val outDir = tempFolder.newFolder("csv_limit_test")
        // Enforce a small limit of 300 bytes (header ~30 bytes, each line ~50 bytes -> ~5 records per file)
        val limitBytes = 300L
        val logger = CSVLogService(maxFileSizeBytes = limitBytes)
        val file1 = logger.startLogging(outDir, "rotation_test")

        // Write 15 records
        for (i in 1..15) {
            logger.logPID("0C", "Engine RPM", 1000.0 + i, "RPM", 1700000000000L + (i * 100))
        }
        logger.stopLogging()

        val files = outDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        assertTrue("Must create multiple rotated part files when size limit is reached", files.size > 1)

        for (f in files) {
            assertTrue("File ${f.name} size (${f.length()}) must be within limit or single boundary record", f.length() <= limitBytes + 100)
            val lines = f.readLines()
            assertEquals("Each rotated file must begin with standard CSV header", "timestamp,pid,name,value,unit", lines.first())
            assertTrue("Each file must contain records", lines.size > 1)
        }
    }

    @Test
    fun testRawTraceLoggerFormattingAndRotation() {
        val outDir = tempFolder.newFolder("trace_test")
        val limitBytes = 400L
        val traceLogger = RawTraceLogger(maxFileSizeBytes = limitBytes)
        val traceFile1 = traceLogger.startTrace(outDir, "debug_trace")

        assertTrue(traceFile1.exists())
        assertEquals("debug_trace.log", traceFile1.name)

        // Log TX and RX sequences
        for (i in 1..10) {
            traceLogger.logTx("010C", 1700000000000L + (i * 200))
            traceLogger.logRx("41 0C 0E 20", 1700000000050L + (i * 200))
        }
        traceLogger.stopTrace()

        val traceFiles = outDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        assertTrue("Must rotate trace files when limit is reached", traceFiles.size > 1)

        val entries = traceLogger.readTrace(traceFile1)
        assertTrue(entries.isNotEmpty())
        assertEquals(TraceDirection.TX, entries[0].direction)
        assertEquals("010C", entries[0].text)
        assertEquals(TraceDirection.RX, entries[1].direction)
        assertEquals("41 0C 0E 20", entries[1].text)
    }

    @Test
    fun testLogListingAndDeletion() {
        val outDir = tempFolder.newFolder("listing_test")
        val csvLogger = CSVLogService()
        val traceLogger = RawTraceLogger()

        val csvFile = csvLogger.startLogging(outDir, "run_01")
        csvLogger.logPID("05", "ECT", 90.0, "°C")
        csvLogger.stopLogging()

        val traceFile = traceLogger.startTrace(outDir, "trace_01")
        traceLogger.logTx("ATI")
        traceLogger.logRx("ELM327 v1.5")
        traceLogger.stopTrace()

        val csvList = csvLogger.listSavedLogs(outDir)
        assertEquals(1, csvList.size)
        assertEquals("run_01.csv", csvList.first().name)
        assertFalse(csvList.first().isRawTrace)

        val traceList = traceLogger.listSavedTraces(outDir)
        assertEquals(1, traceList.size)
        assertEquals("trace_01.log", traceList.first().name)
        assertTrue(traceList.first().isRawTrace)

        // Test deletion
        assertTrue(csvLogger.deleteLog(csvFile))
        assertFalse(csvFile.exists())
        assertEquals(0, csvLogger.listSavedLogs(outDir).size)

        assertTrue(traceLogger.deleteTrace(traceFile))
        assertFalse(traceFile.exists())
        assertEquals(0, traceLogger.listSavedTraces(outDir).size)
    }
}
