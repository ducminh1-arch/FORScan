package com.carscan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import com.carscan.core.viewmodel.LiveViewModel
import com.carscan.core.viewmodel.SessionManager

@Composable
fun TableScreen(
    liveViewModel: LiveViewModel = remember { LiveViewModel() },
    session: SessionManager = SessionManager.instance,
    onBack: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val isImperial by session.isImperial.collectAsState()
    val isDemoMode by session.isDemoMode.collectAsState()
    val supportedPids by liveViewModel.supportedPids.collectAsState()
    val liveValues by liveViewModel.liveValues.collectAsState()
    val minMaxHistory by liveViewModel.minMaxHistory.collectAsState()

    val filteredPids = remember(supportedPids, searchText) {
        if (searchText.isEmpty()) supportedPids
        else supportedPids.filter {
            it.name.contains(searchText, ignoreCase = true) ||
            it.shortName.contains(searchText, ignoreCase = true) ||
            it.pid.contains(searchText, ignoreCase = true)
        }
    }

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
                Text("Live Data Table", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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

        // Search Input
        TextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search PIDs (RPM, Speed, ECT...)", color = Color.Gray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1C212B),
                unfocusedContainerColor = Color(0xFF1C212B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        // Column Titles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PID / PARAMETER", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row {
                Text("CURRENT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Text("MIN / MAX", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
            }
        }

        // List of PIDs
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredPids) { pid ->
                val live = liveValues[pid.id]
                val hist = minMaxHistory[pid.id]
                val unit = if (isImperial) (pid.imperialUnit ?: pid.unit) else pid.unit

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C212B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pid.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("01${pid.pid} • ${pid.category}", color = Color.Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        // Current Value
                        Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
                            Text(live?.formattedString ?: "--", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(unit, color = Color.Gray, fontSize = 9.sp)
                        }

                        // Min / Max
                        Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (hist != null) String.format("%.0f / %.0f", hist.min, hist.max) else "--",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
