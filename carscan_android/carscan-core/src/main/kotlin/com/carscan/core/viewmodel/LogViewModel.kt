package com.carscan.core.viewmodel

import com.carscan.core.logging.CSVLogRecord
import com.carscan.core.logging.LogFileInfo
import com.carscan.core.logging.TraceEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LogViewModel(
    private val session: SessionManager = SessionManager.instance,
    private val logDirectory: File? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val savedLogs = MutableStateFlow<List<LogFileInfo>>(emptyList())
    val selectedLogFile = MutableStateFlow<File?>(null)
    val selectedLogRecords = MutableStateFlow<List<CSVLogRecord>>(emptyList())
    val selectedTraceEntries = MutableStateFlow<List<TraceEntry>>(emptyList())
    val isViewingTrace = MutableStateFlow(false)
    val isLoggingPIDs = session.isLoggingPIDs
    val isTracingCommands = session.isTracingCommands
    val statusMessage = MutableStateFlow<String?>(null)

    fun refreshLogs(dir: File? = logDirectory) {
        val targetDir = dir ?: logDirectory ?: return
        scope.launch(Dispatchers.IO) {
            val csvLogs = session.csvLogger.listSavedLogs(targetDir)
            val traceLogs = session.rawTraceLogger.listSavedTraces(targetDir)
            val all = (csvLogs + traceLogs).sortedByDescending { it.lastModifiedMs }
            savedLogs.value = all
        }
    }

    fun startPIDLogging(dir: File, sessionName: String? = null) {
        try {
            val file = session.startPIDLogging(dir, sessionName)
            statusMessage.value = "Recording live PIDs to ${file.name}"
            refreshLogs(dir)
        } catch (e: Exception) {
            statusMessage.value = "Failed to start logging: ${e.message}"
        }
    }

    fun stopPIDLogging(dir: File? = null) {
        val file = session.stopPIDLogging()
        statusMessage.value = if (file != null) "Saved log to ${file.name}" else "Logging stopped"
        if (dir != null) refreshLogs(dir)
    }

    fun startRawTrace(dir: File, name: String? = null) {
        try {
            val file = session.startRawTracing(dir, name)
            statusMessage.value = "Recording raw command trace to ${file.name}"
            refreshLogs(dir)
        } catch (e: Exception) {
            statusMessage.value = "Failed to start trace: ${e.message}"
        }
    }

    fun stopRawTrace(dir: File? = null) {
        val file = session.stopRawTracing()
        statusMessage.value = if (file != null) "Saved trace to ${file.name}" else "Trace stopped"
        if (dir != null) refreshLogs(dir)
    }

    fun openLog(info: LogFileInfo) {
        selectedLogFile.value = info.file
        isViewingTrace.value = info.isRawTrace
        scope.launch(Dispatchers.IO) {
            if (info.isRawTrace) {
                selectedTraceEntries.value = session.rawTraceLogger.readTrace(info.file)
                selectedLogRecords.value = emptyList()
            } else {
                selectedLogRecords.value = session.csvLogger.readLogRecords(info.file)
                selectedTraceEntries.value = emptyList()
            }
        }
    }

    fun closeLogViewer() {
        selectedLogFile.value = null
        selectedLogRecords.value = emptyList()
        selectedTraceEntries.value = emptyList()
        isViewingTrace.value = false
    }

    fun deleteLog(info: LogFileInfo, dir: File) {
        scope.launch(Dispatchers.IO) {
            val deleted = if (info.isRawTrace) {
                session.rawTraceLogger.deleteTrace(info.file)
            } else {
                session.csvLogger.deleteLog(info.file)
            }
            if (deleted) {
                if (selectedLogFile.value?.absolutePath == info.file.absolutePath) {
                    closeLogViewer()
                }
                refreshLogs(dir)
                statusMessage.value = "Deleted ${info.name}"
            } else {
                statusMessage.value = "Failed to delete ${info.name}"
            }
        }
    }
}
