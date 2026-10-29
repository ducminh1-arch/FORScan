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

data class CSVLogRecord(
    val timestamp: String,
    val pid: String,
    val name: String,
    val value: Double,
    val unit: String
)

data class LogFileInfo(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val recordCount: Int,
    val isRawTrace: Boolean = false
)

class CSVLogService(
    var maxFileSizeBytes: Long = 5 * 1024 * 1024L // Default 5 MB
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private var currentWriter: BufferedWriter? = null
    var currentFile: File? = null
        private set
    var currentSessionName: String = ""
        private set
    private var baseSessionName: String = ""
    private var partIndex: Int = 1
    private var currentFileBytes: Long = 0L
    private var targetDirectory: File? = null
    var isLoggingActive: Boolean = false
        private set

    companion object {
        const val CSV_HEADER = "timestamp,pid,name,value,unit"
    }

    @Synchronized
    fun startLogging(outputDir: File, sessionName: String? = null): File {
        stopLogging()
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        targetDirectory = outputDir
        partIndex = 1
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        baseSessionName = sessionName?.trim()?.takeIf { it.isNotEmpty() } ?: "live_log_$timeStamp"
        return createNewFileAndWriter()
    }

    private fun createNewFileAndWriter(): File {
        val dir = targetDirectory ?: throw IllegalStateException("Output directory not set")
        val fileName = if (partIndex == 1) "$baseSessionName.csv" else "${baseSessionName}_part$partIndex.csv"
        val file = File(dir, fileName)
        currentFile = file
        currentSessionName = file.name

        val writer = BufferedWriter(FileWriter(file, false))
        writer.write(CSV_HEADER)
        writer.newLine()
        writer.flush()

        currentFileBytes = file.length()
        currentWriter = writer
        isLoggingActive = true
        return file
    }

    @Synchronized
    fun logPID(
        pid: String,
        name: String,
        value: Double,
        unit: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (!isLoggingActive || currentWriter == null) return

        val formattedTime = isoFormat.format(Date(timestampMs))
        val safeName = if (name.contains(",")) "\"$name\"" else name
        val safeUnit = if (unit.contains(",")) "\"$unit\"" else unit
        val line = "$formattedTime,$pid,$safeName,$value,$safeUnit"
        val lineBytes = (line + "\n").toByteArray(Charsets.UTF_8).size

        // Enforce maximum file size limit with automatic part rotation
        if (currentFileBytes + lineBytes > maxFileSizeBytes) {
            currentWriter?.flush()
            currentWriter?.close()
            partIndex++
            createNewFileAndWriter()
        }

        currentWriter?.let { writer ->
            writer.write(line)
            writer.newLine()
            writer.flush()
            currentFileBytes += lineBytes
        }
    }

    @Synchronized
    fun stopLogging(): File? {
        val file = currentFile
        try {
            currentWriter?.flush()
            currentWriter?.close()
        } catch (_: Exception) {}
        currentWriter = null
        isLoggingActive = false
        return file
    }

    fun listSavedLogs(dir: File): List<LogFileInfo> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray()
        return files.map { f ->
            val count = countRecords(f)
            LogFileInfo(
                file = f,
                name = f.name,
                sizeBytes = f.length(),
                lastModifiedMs = f.lastModified(),
                recordCount = count,
                isRawTrace = false
            )
        }.sortedByDescending { it.lastModifiedMs }
    }

    fun readLogRecords(file: File): List<CSVLogRecord> {
        if (!file.exists()) return emptyList()
        val records = mutableListOf<CSVLogRecord>()
        BufferedReader(FileReader(file)).use { reader ->
            reader.readLine() // skip CSV header
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    parseCSVLine(trimmed)?.let { records.add(it) }
                }
                line = reader.readLine()
            }
        }
        return records
    }

    fun parseCSVLine(line: String): CSVLogRecord? {
        val parts = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (ch in line) {
            when (ch) {
                '\"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else {
                        parts.add(sb.toString().trim())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        parts.add(sb.toString().trim())

        if (parts.size >= 5) {
            val ts = parts[0]
            val pid = parts[1]
            val name = parts[2]
            val value = parts[3].toDoubleOrNull() ?: 0.0
            val unit = parts[4]
            return CSVLogRecord(ts, pid, name, value, unit)
        }
        return null
    }

    private fun countRecords(file: File): Int {
        var count = 0
        try {
            BufferedReader(FileReader(file)).use { reader ->
                reader.readLine() // skip header
                while (reader.readLine() != null) {
                    count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun deleteLog(file: File): Boolean {
        return try {
            if (currentFile?.absolutePath == file.absolutePath) {
                stopLogging()
            }
            file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
