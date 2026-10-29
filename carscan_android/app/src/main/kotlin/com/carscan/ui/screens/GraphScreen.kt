package com.carscan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.core.viewmodel.LiveViewModel
import com.carscan.core.viewmodel.SessionManager
import com.carscan.ui.components.DemoChip
import kotlin.math.roundToInt

@Composable
fun GraphScreen(
    liveViewModel: LiveViewModel = remember { LiveViewModel() },
    session: SessionManager = SessionManager.instance,
    onBack: () -> Unit
) {
    val supportedPids by liveViewModel.supportedPids.collectAsState()
    val selectedPids by liveViewModel.selectedPidsForGraph.collectAsState()
    val liveValues by liveViewModel.liveValues.collectAsState()
    val graphBuffers by liveViewModel.graphBuffers.collectAsState()
    val isImperial by session.isImperial.collectAsState()
    val isDemoMode by session.isDemoMode.collectAsState()

    val seriesColors = listOf(Color(0xFF00E5FF), Color(0xFFFF9100), Color(0xFF00E676), Color(0xFFE040FB))
    val activePids = supportedPids.filter { selectedPids.contains(it.id) }

    DisposableEffect(selectedPids) {
        liveViewModel.startPolling(activePids)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Cyan)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Graph Plotter", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (isDemoMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DemoChip()
                }
            }
            Button(
                onClick = { session.isImperial.value = !isImperial },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan.copy(alpha = 0.2f))
            ) {
                Text(if (isImperial) "US" else "METRIC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Horizontal Chip Selector (Max 4 PIDs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            supportedPids.forEach { pid ->
                val isSelected = selectedPids.contains(pid.id)
                val chipColor = if (isSelected) {
                    val idx = selectedPids.indexOf(pid.id)
                    seriesColors[idx % seriesColors.size]
                } else Color.Gray

                FilterChip(
                    selected = isSelected,
                    onClick = { liveViewModel.toggleGraphPid(pid.id) },
                    label = { Text(pid.shortName, fontSize = 12.sp) },
                    leadingIcon = if (isSelected) {
                        { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(chipColor)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2A3548),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1C212B),
                        labelColor = Color.Gray
                    )
                )
            }
        }

        // Dedicated Scale & Value Cards per active PID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            activePids.forEachIndexed { index, pid ->
                val color = seriesColors[index % seriesColors.size]
                val live = liveValues[pid.id]?.formattedString ?: "--"
                val unit = if (isImperial) (pid.imperialUnit ?: pid.unit) else pid.unit
                val minVal = if (isImperial && pid.imperialUnit != null) (pid.min * 0.621371).roundToInt() else pid.min.roundToInt()
                val maxVal = if (isImperial && pid.imperialUnit != null) (pid.max * 0.621371).roundToInt() else pid.max.roundToInt()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1C212B))
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${pid.shortName}: $live $unit",
                                color = color,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Scale: $minVal..$maxVal $unit",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Calculate time span of collected points (in seconds)
        val allPoints = activePids.flatMap { graphBuffers[it.id] ?: emptyList() }
        val timeSpanSec = remember(allPoints.size) {
            if (allPoints.size >= 2) {
                val minTime = allPoints.minOf { it.timestampMs }
                val maxTime = allPoints.maxOf { it.timestampMs }
                val span = ((maxTime - minTime) / 1000L).coerceAtLeast(10L)
                span
            } else 60L
        }

        // Canvas Chart Container
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // Plot Area with Left & Right Y-Axis Scale Markers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Left Y-Axis Scale Labels (Primary PID, activePids[0])
                    val leftPid = activePids.getOrNull(0)
                    if (leftPid != null) {
                        val color0 = seriesColors[0]
                        val unit0 = if (isImperial) (leftPid.imperialUnit ?: leftPid.unit) else leftPid.unit
                        val min0 = if (isImperial && leftPid.imperialUnit != null) (leftPid.min * 0.621371).roundToInt() else leftPid.min.roundToInt()
                        val max0 = if (isImperial && leftPid.imperialUnit != null) (leftPid.max * 0.621371).roundToInt() else leftPid.max.roundToInt()
                        val mid0 = ((min0 + max0) / 2)

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(end = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("$max0 $unit0", color = color0, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$mid0", color = color0.copy(alpha = 0.6f), fontSize = 9.sp)
                            Text("$min0 $unit0", color = color0, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Main Canvas Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw horizontal grid lines (5 levels: 0%, 25%, 50%, 75%, 100%)
                            val horizontalSteps = 4
                            for (i in 0..horizontalSteps) {
                                val y = size.height * (i.toFloat() / horizontalSteps)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Draw vertical time grid lines (4 intervals: 0%, 25%, 50%, 75%, 100%)
                            val verticalSteps = 4
                            for (i in 0..verticalSteps) {
                                val x = size.width * (i.toFloat() / verticalSteps)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                            }

                            // Draw Multi-series Line Traces with normalized scaling
                            activePids.forEachIndexed { index, pid ->
                                val color = seriesColors[index % seriesColors.size]
                                val points = graphBuffers[pid.id] ?: emptyList()

                                if (points.size > 1) {
                                    val path = Path()
                                    val stepX = size.width / maxOf(1f, (points.size - 1).toFloat())
                                    val minVal = pid.min.toFloat()
                                    val maxVal = pid.max.toFloat()
                                    val range = maxOf(1f, maxVal - minVal)

                                    points.forEachIndexed { ptIdx, pt ->
                                        val normY = ((pt.value.toFloat() - minVal) / range).coerceIn(0f, 1f)
                                        val x = ptIdx * stepX
                                        val y = size.height - (normY * size.height)

                                        if (ptIdx == 0) path.moveTo(x, y)
                                        else path.lineTo(x, y)
                                    }

                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }
                    }

                    // Right Y-Axis Scale Labels (Secondary PID, activePids[1] if present)
                    val rightPid = activePids.getOrNull(1)
                    if (rightPid != null) {
                        val color1 = seriesColors[1]
                        val unit1 = if (isImperial) (rightPid.imperialUnit ?: rightPid.unit) else rightPid.unit
                        val min1 = if (isImperial && rightPid.imperialUnit != null) (rightPid.min * 0.621371).roundToInt() else rightPid.min.roundToInt()
                        val max1 = if (isImperial && rightPid.imperialUnit != null) (rightPid.max * 0.621371).roundToInt() else rightPid.max.roundToInt()
                        val mid1 = ((min1 + max1) / 2)

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("$max1 $unit1", color = color1, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$mid1", color = color1.copy(alpha = 0.6f), fontSize = 9.sp)
                            Text("$min1 $unit1", color = color1, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // X-Axis Time Labels Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (activePids.isNotEmpty()) 48.dp else 8.dp, end = if (activePids.size > 1) 48.dp else 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val t = timeSpanSec
                    Text(text = "-${t}s", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "-${(t * 3) / 4}s", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "-${t / 2}s", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "-${t / 4}s", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "0s (Now)", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Buffer info footer with navigation bars padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Time window: ~${timeSpanSec}s | Buffer: 300 pts",
                color = Color.Gray,
                fontSize = 11.sp
            )
            Text(
                text = if (activePids.isNotEmpty()) "${activePids.size} PID(s) plotted" else "Select PID above",
                color = if (activePids.isNotEmpty()) Color.Cyan else Color.DarkGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
