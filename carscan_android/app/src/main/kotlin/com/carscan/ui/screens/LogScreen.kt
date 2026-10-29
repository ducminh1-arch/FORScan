package com.carscan.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.core.logging.LogFileInfo
import com.carscan.core.viewmodel.LogViewModel
import com.carscan.core.viewmodel.SessionManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    session: SessionManager = SessionManager.instance,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logDir = remember { File(context.filesDir, "logs").apply { if (!exists()) mkdirs() } }
    val viewModel = remember { LogViewModel(session, logDir) }

    LaunchedEffect(Unit) {
        viewModel.refreshLogs(logDir)
    }

    val savedLogs by viewModel.savedLogs.collectAsState()
    val isLoggingPIDs by viewModel.isLoggingPIDs.collectAsState()
    val isTracingCommands by viewModel.isTracingCommands.collectAsState()
    val selectedLogFile by viewModel.selectedLogFile.collectAsState()
    val selectedLogRecords by viewModel.selectedLogRecords.collectAsState()
    val selectedTraceEntries by viewModel.selectedTraceEntries.collectAsState()
    val isViewingTrace by viewModel.isViewingTrace.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isDemoMode by session.isDemoMode.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf<LogFileInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Logs & Trace", color = Color.White)
                        if (isDemoMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            com.carscan.ui.components.DemoChip()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLogs(logDir) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E2430))
            )
        },
        containerColor = Color(0xFF12151A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Status banner if active
            statusMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF233044))
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFF64B5F6),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Quick Actions: Live PID Logging & Raw Trace Toggles
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PID Logging Button
                Button(
                    onClick = {
                        if (isLoggingPIDs) {
                            viewModel.stopPIDLogging(logDir)
                        } else {
                            viewModel.startPIDLogging(logDir)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoggingPIDs) Color(0xFFE53935) else Color(0xFF2E7D32)
                    )
                ) {
                    Icon(
                        if (isLoggingPIDs) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isLoggingPIDs) "Stop PID Log" else "Record PIDs", fontSize = 12.sp)
                }

                // Raw Command Trace Button
                Button(
                    onClick = {
                        if (isTracingCommands) {
                            viewModel.stopRawTrace(logDir)
                        } else {
                            viewModel.startRawTrace(logDir)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracingCommands) Color(0xFFD84315) else Color(0xFF1565C0)
                    )
                ) {
                    Icon(
                        if (isTracingCommands) Icons.Default.Stop else Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isTracingCommands) "Stop Trace" else "Raw Trace", fontSize = 12.sp)
                }
            }

            // Detail Viewer if a file is open
            if (selectedLogFile != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedLogFile?.name ?: "",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isViewingTrace) "${selectedTraceEntries.size} commands" else "${selectedLogRecords.size} PID records",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    selectedLogFile?.let { shareFile(context, it) }
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF64B5F6))
                                }
                                IconButton(onClick = { viewModel.closeLogViewer() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF2C3545), modifier = Modifier.padding(vertical = 8.dp))

                        if (isViewingTrace) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(selectedTraceEntries) { entry ->
                                    val isTx = entry.direction.name == "TX"
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(
                                            text = "[${entry.timestamp.substringAfter("T").replace("Z", "")}] ",
                                            color = Color(0xFF90A4AE),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${entry.direction.name}: ",
                                            color = if (isTx) Color(0xFF4FC3F7) else Color(0xFF81C784),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = entry.text,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Time", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                        Text("PID", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                        Text("Name", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                                        Text("Value", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text("Unit", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                    }
                                    HorizontalDivider(color = Color(0xFF2C3545))
                                }
                                items(selectedLogRecords) { record ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(record.timestamp.substringAfter("T").replace("Z", ""), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                        Text(record.pid, color = Color(0xFF00E676), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.8f))
                                        Text(record.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.5f))
                                        Text(String.format("%.1f", record.value), color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text(record.unit, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // List of saved files
                Text(
                    text = "Saved Log Sessions (${savedLogs.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (savedLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs recorded yet.\nStart polling and tap 'Record PIDs' or 'Raw Trace'.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedLogs) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openLog(item) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (item.isRawTrace) Icons.Default.Code else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (item.isRawTrace) Color(0xFFFFB300) else Color(0xFF00E676),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(
                                            text = "${formatSize(item.sizeBytes)} • ${item.recordCount} ${if (item.isRawTrace) "lines" else "records"}",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = { shareFile(context, item.file) }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF64B5F6))
                                    }
                                    IconButton(onClick = { showDeleteConfirmDialog = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteConfirmDialog?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Log File") },
            text = { Text("Are you sure you want to delete ${fileToDelete.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLog(fileToDelete, logDir)
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".csv")) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export / Share Log"))
    } catch (_: Exception) {
        val text = file.readText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
        }
        context.startActivity(Intent.createChooser(intent, "Export / Share Log"))
    }
}
