package com.carscan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.core.viewmodel.LiveViewModel
import com.carscan.core.viewmodel.SessionManager

@Composable
fun DashboardScreen(
    liveViewModel: LiveViewModel = remember { LiveViewModel() },
    session: SessionManager = SessionManager.instance,
    onBack: () -> Unit
) {
    val liveValues by liveViewModel.liveValues.collectAsState()
    val isImperial by session.isImperial.collectAsState()
    val isDemoMode by session.isDemoMode.collectAsState()
    val supportedPids by liveViewModel.supportedPids.collectAsState()

    DisposableEffect(Unit) {
        liveViewModel.startPolling(supportedPids)
        onDispose {
            liveViewModel.stopPolling()
        }
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
                Text("Dashboard", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (isDemoMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    com.carscan.ui.components.DemoChip()
                }
            }
            Button(
                onClick = { session.isImperial.value = !isImperial },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan.copy(alpha = 0.2f))
            ) {
                Text(if (isImperial) "US" else "METRIC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // 2 Circular Gauges: RPM and Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val rpm = liveValues["PID_010C"]?.numericValue ?: 850.0
                Box(modifier = Modifier.weight(1f)) {
                    ComposeCircularGauge(
                        title = "RPM",
                        value = rpm,
                        maxVal = 8000.0,
                        unit = "RPM",
                        color = Color(0xFFFF9800)
                    )
                }

                val speed = liveValues["PID_010D"]?.numericValue ?: 45.0
                Box(modifier = Modifier.weight(1f)) {
                    ComposeCircularGauge(
                        title = "SPEED",
                        value = speed,
                        maxVal = if (isImperial) 160.0 else 240.0,
                        unit = if (isImperial) "mph" else "km/h",
                        color = Color.Cyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Secondary Metric Cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val ect = liveValues["PID_0105"]?.numericValue ?: 90.0
                    ComposeMetricCard("Coolant (ECT)", String.format("%.1f", ect), if (isImperial) "°F" else "°C", Color(0xFF4CAF50))
                }
                item {
                    val thr = liveValues["PID_0111"]?.numericValue ?: 15.0
                    ComposeMetricCard("Throttle (TP)", String.format("%.1f", thr), "%", Color(0xFF2196F3))
                }
                item {
                    val load = liveValues["PID_0104"]?.numericValue ?: 22.0
                    ComposeMetricCard("Engine Load", String.format("%.1f", load), "%", Color(0xFFFFEB3B))
                }
                item {
                    val v = liveValues["PID_0142"]?.numericValue ?: 12.6
                    ComposeMetricCard("ECU Voltage", String.format("%.1f", v), "V", Color(0xFF00E676))
                }
            }
        }
    }
}

@Composable
fun ComposeCircularGauge(
    title: String,
    value: Double,
    maxVal: Double,
    unit: String,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
        modifier = Modifier.fillMaxWidth().height(160.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val sweepRatio = (value / maxVal).coerceIn(0.0, 1.0).toFloat()
            Canvas(modifier = Modifier.size(130.dp)) {
                // Background Track
                drawArc(
                    color = Color.White.copy(alpha = 0.1f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                )
                // Value Arc
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * sweepRatio,
                    useCenter = false,
                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(String.format("%.0f", value), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ComposeMetricCard(title: String, value: String, unit: String, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}
