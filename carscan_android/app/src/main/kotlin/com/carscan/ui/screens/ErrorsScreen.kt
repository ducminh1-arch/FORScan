package com.carscan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.core.models.ModuleScanStatus
import com.carscan.core.viewmodel.ErrorsViewModel

@Composable
fun ErrorsScreen(
    viewModel: ErrorsViewModel = remember { ErrorsViewModel() },
    onBack: () -> Unit
) {
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isClearing by viewModel.isClearing.collectAsState()
    val scanningModule by viewModel.scanningModuleAcronym.collectAsState()
    val showClearDialog by viewModel.showClearConfirmDialog.collectAsState()
    val selectedModule by viewModel.selectedModuleForClear.collectAsState()
    val alertMessage by viewModel.alertMessage.collectAsState()
    val isDemoMode by com.carscan.core.viewmodel.SessionManager.instance.isDemoMode.collectAsState()
    var expandedModuleId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (scanResults.isEmpty()) {
            viewModel.scanDTCs()
        }
    }

    if (showClearDialog && selectedModule != null) {
        val mod = selectedModule!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDialog() },
            title = {
                Text(
                    text = "Clear DTCs for ${mod.acronym}?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Module: ${mod.name} [TX: ${mod.txId}, Bus: ${mod.bus}]",
                        color = Color.Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SAFETY WARNING: Clearing diagnostic information (UDS Service 0x14) will reset fault logs, freeze frames, and readiness adaptation parameters for this module. Are you sure you want to proceed?",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeClearDTCsForModule() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    enabled = !isClearing
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Clear ${mod.acronym} DTCs", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDialog() }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2530)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12151A))
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A202C))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Cyan)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Module DTC Diagnostics", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (isDemoMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        com.carscan.ui.components.DemoChip()
                    }
                }
                Text("UDS 0x19 / Multi-bus CAN", color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = { viewModel.scanDTCs() }, enabled = !isScanning) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = if (isScanning) Color.DarkGray else Color.Cyan)
            }
        }

        if (isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Cyan)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Querying ${scanningModule ?: "modules"} via UDS 0x19...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Switching HS-CAN / MS-CAN buses automatically",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scanResults) { result ->
                    val isExpanded = expandedModuleId == result.module.id

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedModuleId = if (isExpanded) null else result.module.id
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${result.module.acronym} - ${result.module.name}",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Bus badge
                                        Text(
                                            text = result.module.bus,
                                            color = Color.Cyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("•", color = Color.Gray, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        // Status
                                        val statusColor = when (result.status) {
                                            is ModuleScanStatus.Clean -> Color(0xFF4CAF50)
                                            is ModuleScanStatus.FaultsFound -> Color(0xFFFF9800)
                                            is ModuleScanStatus.OfflineNoResponse -> Color(0xFF9E9E9E)
                                            is ModuleScanStatus.ScanError -> Color(0xFFE57373)
                                        }
                                        Text(
                                            text = result.status.displayText,
                                            color = statusColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                val currentStatus = result.status
                                if (currentStatus is ModuleScanStatus.FaultsFound) {
                                    val count = currentStatus.dtcs.size
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFFF9800))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(10.dp))

                                // Module Details & Verification Flag
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TX ID: ${result.module.txId} | RX ID: ${result.module.rxId}", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = if (result.module.verified) "Verified" else "Bench unverified",
                                        color = if (result.module.verified) Color.Green else Color.DarkGray,
                                        fontSize = 10.sp
                                    )
                                }

                                val expandedStatus = result.status
                                if (expandedStatus is ModuleScanStatus.FaultsFound) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    expandedStatus.dtcs.forEach { dtc ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Red.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = dtc.code,
                                                    color = Color.Red,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(dtc.description, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                dtc.symptom?.let { symptom ->
                                                    Text(symptom, color = Color.Gray, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    // Individual Module Clear Button
                                    Button(
                                        onClick = { viewModel.requestClearDTCs(result.module) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Clear ${result.module.acronym} DTCs (UDS 0x14)", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Extra bottom spacer so the last module (e.g. IPC) scrolls completely clear of bottom action button
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Bottom Actions (Re-scan All Modules) with navigationBarsPadding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161A22))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.scanDTCs() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-scan All Modules", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
