package com.carscan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.carscan.core.viewmodel.VehicleViewModel

@Composable
fun VehicleScreen(
    viewModel: VehicleViewModel = remember { VehicleViewModel() },
    onBack: () -> Unit
) {
    val vin by viewModel.vin.collectAsState()
    val make by viewModel.make.collectAsState()
    val model by viewModel.model.collectAsState()
    val year by viewModel.year.collectAsState()
    val atrvVoltage by viewModel.atrvVoltage.collectAsState()
    val vpwrVoltage by viewModel.vpwrVoltage.collectAsState()
    val modules by viewModel.detectedModules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDemoMode by com.carscan.core.viewmodel.SessionManager.instance.isDemoMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12151A))
    ) {
        // Header
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vehicle Information", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (isDemoMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    com.carscan.ui.components.DemoChip()
                }
            }
            IconButton(onClick = { viewModel.loadData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Cyan)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Info Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color.Cyan,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("$year $make $model", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("VIN: $vin", color = Color.Gray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Battery Voltage Comparison Card (ATRV vs PID 0142)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Battery & Power Supply", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // ATRV Box
                            VoltageBox(
                                title = "OBD Port (ATRV)",
                                voltage = if (atrvVoltage > 0) atrvVoltage else 12.6,
                                subtitle = "Pin 16 Direct ADC",
                                iconColor = Color(0xFFFF9800),
                                modifier = Modifier.weight(1f)
                            )
                            // VPWR Box
                            VoltageBox(
                                title = "ECU (PID 0142)",
                                voltage = if (vpwrVoltage > 0) vpwrVoltage else 12.5,
                                subtitle = "Internal VPWR",
                                iconColor = Color.Cyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Detected Modules (Broadcast 7E8-7EF)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Detected Modules (7E8–7EF)", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${modules.size} found", color = Color.Green, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.Cyan)
                            }
                        } else {
                            modules.forEach { mod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF273142))
                                    ) {
                                        Text(mod.acronym, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mod.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("Rx ID: ${mod.canId} • ${mod.bus}", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Green))
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoltageBox(
    title: String,
    voltage: Double,
    subtitle: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF232A36))
            .padding(12.dp)
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(String.format("%.1f V", voltage), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontSize = 9.sp)
        }
    }
}
