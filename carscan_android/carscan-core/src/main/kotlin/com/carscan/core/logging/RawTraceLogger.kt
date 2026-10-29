package com.carscan.core.logging

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TraceEntry(
    val timestamp: String,
    val direction: TraceDirection,
    val text: String
)

enum class TraceDirection { TX, RX }

class RawTraceLogger(
    var maxFileSizeBytes: Long = 10 * 1024 * 1024L // Default 10 MB limit for trace logs
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private var currentWriter: BufferedWriter? = null
    var currentFile: File? = null
        private set
    private var baseName: String = ""
    private var partIndex: Int = 1
    private var currentFileBytes: Long = 0L
    private var targetDirectory: File? = null
    var isTracingActive: Boolean = false
        private set

    @Synchronized
    fun startTrace(outputDir: File, name: String? = null): File {
        stopTrace()
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        targetDirectory = outputDir
        partIndex = 1
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        baseName = name?.trim()?.takeIf { it.isNotEmpty() } ?: "carscan_raw_trace_$timeStamp"
        return createTraceFile()
    }

    private fun createTraceFile(): File {
        val dir = targetDirectory ?: throw IllegalStateException("Trace directory not configured")
        val fileName = if (partIndex == 1) "$baseName.log" else "${baseName}_part$partIndex.log"
        val file = File(dir, fileName)
        currentFile = file

        val writer = BufferedWriter(FileWriter(file, false))
        val header = "# CarScan Lite Raw Command Trace Log\n# Started: ${isoFormat.format(Date())}\n# Format: [TIMESTAMP] <TX|RX>: <COMMAND|RESPONSE>\n\n"
        writer.write(header)
        writer.flush()

        currentFileBytes = file.length()
        currentWriter = writer
        isTracingActive = true
        return file
    }

    @Synchronized
    fun logTx(command: String, timestampMs: Long = System.currentTimeMillis()) {
        logLine(TraceDirection.TX, command, timestampMs)
    }

    @Synchronized
    fun logRx(response: String, timestampMs: Long = System.currentTimeMillis()) {
        logLine(TraceDirection.RX, response, timestampMs)
    }

    private fun logLine(direction: TraceDirection, content: String, timestampMs: Long) {
        if (!isTracingActive || currentWriter == null) return

        val formattedTime = isoFormat.format(Date(timestampMs))
        val line = "[$formattedTime] ${direction.name}: $content"
        val lineBytes = (line + "\n").toByteArray(Charsets.UTF_8).size

        // Enforce maximum file size limit
        if (currentFileBytes + lineBytes > maxFileSizeBytes) {
            currentWriter?.flush()
            currentWriter?.close()
            partIndex++
            createTraceFile()
        }

        currentWriter?.let { writer ->
            writer.write(line)
            writer.newLine()
            writer.flush()
            currentFileBytes += lineBytes
        }
    }

    @Synchronized
    fun stopTrace(): File? {
        val file = currentFile
        try {
            currentWriter?.flush()
            currentWriter?.close()
        } catch (_: Exception) {}
        currentWriter = null
        isTracingActive = false
        return file
    }

    fun listSavedTraces(dir: File): List<LogFileInfo> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: emptyArray()
        return files.map { f ->
            val count = countLines(f)
            LogFileInfo(
                file = f,
                name = f.name,
                sizeBytes = f.length(),
                lastModifiedMs = f.lastModified(),
                recordCount = count,
                isRawTrace = true
            )
        }.sortedByDescending { it.lastModifiedMs }
    }

    fun readTrace(file: File): List<TraceEntry> {
        if (!file.exists()) return emptyList()
        val entries = mutableListOf<TraceEntry>()
        BufferedReader(FileReader(file)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.contains("]")) {
                    val closeIdx = trimmed.indexOf("]")
                    if (closeIdx > 1) {
                        val ts = trimmed.substring(1, closeIdx)
                        val rest = trimmed.substring(closeIdx + 1).trim()
                        val colonIdx = rest.indexOf(":")
                        if (colonIdx > 0) {
                            val dirStr = rest.substring(0, colonIdx).trim()
                            val payload = rest.substring(colonIdx + 1).trim()
                            val dir = if (dirStr.uppercase() == "TX") TraceDirection.TX else TraceDirection.RX
                            entries.add(TraceEntry(ts, dir, payload))
                        }
                    }
                }
                line = reader.readLine()
            }
        }
        return entries
    }

    private fun countLines(file: File): Int {
        var count = 0
        try {
            BufferedReader(FileReader(file)).use { reader ->
                while (reader.readLine() != null) count++
            }
        } catch (_: Exception) {}
        return count
    }

    fun deleteTrace(file: File): Boolean {
        return try {
            if (currentFile?.absolutePath == file.absolutePath) {
                stopTrace()
            }
            file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
