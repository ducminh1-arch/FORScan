package com.carscan.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.core.transport.ConnectionState
import com.carscan.core.viewmodel.SessionManager
import kotlin.math.cos
import kotlin.math.sin

enum class MenuId {
    VEHICLE, LOG, ERRORS, DASHBOARD, TABLE, GRAPH, TESTS, SERVICES
}

data class MenuTileSpec(
    val id: MenuId,
    val title: String,
    val route: String
)

@Composable
fun HomeScreen(
    session: SessionManager = SessionManager.instance,
    onNavigate: (String) -> Unit
) {
    val connectionState by session.connectionState.collectAsState()
    val isDemoMode by session.isDemoMode.collectAsState()
    val voltage by session.adapterVoltage.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val menuTiles = listOf(
        MenuTileSpec(MenuId.VEHICLE, "Vehicle", "vehicle"),
        MenuTileSpec(MenuId.LOG, "Log", "log"),
        MenuTileSpec(MenuId.ERRORS, "Errors", "errors"),
        MenuTileSpec(MenuId.DASHBOARD, "Dashboard", "dashboard"),
        MenuTileSpec(MenuId.TABLE, "Table", "table"),
        MenuTileSpec(MenuId.GRAPH, "Graph", "graph"),
        MenuTileSpec(MenuId.TESTS, "Tests", "tests"),
        MenuTileSpec(MenuId.SERVICES, "Services", "services")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF22262E),
                        Color(0xFF181B20),
                        Color(0xFF121418)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
        ) {
            // Top Bar (Skeuomorphic Automotive Header Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2C313C), Color(0xFF1C2027))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF383E4C).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection indicator dot on left
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF00E676) else Color(0xFFFF5252))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isConnected) (if (isDemoMode) "DEMO" else "LIVE") else "OFFLINE",
                        color = if (isConnected) Color(0xFF00E676) else Color(0xFF9098A8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Title in Center
                Text(
                    text = "CarScan Lite",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.weight(1f))

                // Info & Demo Pill on Right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Demo toggle button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF15181F))
                            .border(
                                1.dp,
                                if (isDemoMode) Color(0xFFFFB300).copy(alpha = 0.6f) else Color(0xFF3A4150),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { session.isDemoMode.value = !isDemoMode }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Demo",
                            color = if (isDemoMode) Color(0xFFFFB300) else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Info button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF353C4A), Color(0xFF20242D))
                                )
                            )
                            .border(1.dp, Color(0xFF4A5265), RoundedCornerShape(8.dp))
                            .clickable { onNavigate("vehicle") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color(0xFFD0D5E0),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Settings button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF353C4A), Color(0xFF20242D))
                                )
                            )
                            .border(1.dp, Color(0xFF4A5265), RoundedCornerShape(8.dp))
                            .clickable { onNavigate("settings") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFD0D5E0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 8-Icon Grid (3 rows: 3 - 3 - 2)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(menuTiles) { tile ->
                    SkeuomorphicMenuTile(tile = tile) {
                        onNavigate(tile.route)
                    }
                }
            }
        }

        // Signature Bottom Automotive Console Bar with Protruding Center Speedometer Dial
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp)
        ) {
            // Horizontal Bar Base
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF282D37), Color(0xFF16181E))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF3C4252),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: CONNECT Button
                    MetallicActionButton(
                        text = if (isConnecting) "CONNECTING" else "CONNECT",
                        isActive = !isConnected && !isConnecting,
                        highlightColor = Color(0xFF00E676),
                        modifier = Modifier.width(115.dp)
                    ) {
                        if (!isConnected) session.connect()
                    }

                    Spacer(modifier = Modifier.width(68.dp)) // Space for center dial

                    // Right: DISCONNECT Button
                    MetallicActionButton(
                        text = "DISCONNECT",
                        isActive = isConnected,
                        highlightColor = Color(0xFFFF5252),
                        modifier = Modifier.width(115.dp)
                    ) {
                        if (isConnected) session.disconnect()
                    }
                }
            }

            // Protruding Analog Center Speedometer / Gauge Dial
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
                    .size(80.dp)
                    .shadow(elevation = 12.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF555C6E), Color(0xFF1A1C22), Color(0xFF454B5A))
                        )
                    )
                    .border(2.dp, Color(0xFF70788C), CircleShape)
                    .clickable {
                        if (isConnected) session.disconnect() else session.connect()
                    }
            ) {
                CenterSpeedometerGauge(
                    isConnected = isConnected,
                    voltage = voltage
                )
            }
        }
    }
}

/**
 * Skeuomorphic Menu Tile with automotive 3D bevel and rich custom vector icon
 */
@Composable
fun SkeuomorphicMenuTile(
    tile: MenuTileSpec,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // Tile Plate
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2C323E), Color(0xFF191C22))
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF4D5568), Color(0xFF242730))
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            // Render rich skeuomorphic custom icon
            when (tile.id) {
                MenuId.VEHICLE -> VehicleIconGraphic()
                MenuId.LOG -> LogIconGraphic()
                MenuId.ERRORS -> ErrorsIconGraphic()
                MenuId.DASHBOARD -> DashboardIconGraphic()
                MenuId.TABLE -> TableIconGraphic()
                MenuId.GRAPH -> GraphIconGraphic()
                MenuId.TESTS -> TestsIconGraphic()
                MenuId.SERVICES -> ServicesIconGraphic()
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title Label
        Text(
            text = tile.title,
            color = Color(0xFFE0E4EC),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Metallic Action Button (CONNECT / DISCONNECT) matching FORScan Lite bezel buttons
 */
@Composable
fun MetallicActionButton(
    text: String,
    isActive: Boolean,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    if (isActive) listOf(Color(0xFF383F4E), Color(0xFF20242D))
                    else listOf(Color(0xFF22252C), Color(0xFF16181D))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (isActive) listOf(highlightColor.copy(alpha = 0.8f), Color(0xFF343A48))
                    else listOf(Color(0xFF3A404E), Color(0xFF1F2228))
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = isActive) { onClick() }
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else Color(0xFF6B7280),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Center Speedometer / Gauge Dial on bottom console
 */
@Composable
fun CenterSpeedometerGauge(
    isConnected: Boolean,
    voltage: Double
) {
    val infiniteTransition = rememberInfiniteTransition(label = "needleJitter")
    val needleJitter by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jitter"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 4f

            // Dark dial face
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF14171E), Color(0xFF090B0E)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Arc ticks (Green -> Yellow -> Red)
            val startAngle = 140f
            val sweepAngle = 260f

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF00E676), Color(0xFFFFB300), Color(0xFFFF5252)),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 3f)
            )

            // Small tick marks
            for (i in 0..12) {
                val angleDeg = startAngle + (i / 12f) * sweepAngle
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val innerR = radius - 8f
                val outerR = radius - 2f
                val p1 = Offset(
                    (center.x + innerR * cos(angleRad)).toFloat(),
                    (center.y + innerR * sin(angleRad)).toFloat()
                )
                val p2 = Offset(
                    (center.x + outerR * cos(angleRad)).toFloat(),
                    (center.y + outerR * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (i % 3 == 0) 2.5f else 1.2f
                )
            }

            // Needle
            val targetNeedleAngle = if (isConnected) (startAngle + sweepAngle * 0.45f + needleJitter) else (startAngle + 5f)
            val needleRad = Math.toRadians(targetNeedleAngle.toDouble())
            val needleLen = radius - 10f
            val needleTip = Offset(
                (center.x + needleLen * cos(needleRad)).toFloat(),
                (center.y + needleLen * sin(needleRad)).toFloat()
            )

            // Draw needle line
            drawLine(
                color = Color(0xFFFF3D00),
                start = center,
                end = needleTip,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // Center chrome hub
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFBAC2D1), Color(0xFF454B57)),
                    center = center,
                    radius = 7f
                ),
                radius = 7f,
                center = center
            )
            drawCircle(
                color = Color(0xFFFF3D00),
                radius = 3f,
                center = center
            )

            // Top glossy lens reflection arc
            drawArc(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                    startY = center.y - radius,
                    endY = center.y
                ),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true
            )
        }

        if (isConnected && voltage > 0) {
            Text(
                text = String.format("%.1fV", voltage),
                color = Color(0xFF00E676),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

// ==========================================
// 8 Skeuomorphic Automotive Icons
// ==========================================

@Composable
fun VehicleIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // Blue metallic disc
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF29B6F6), Color(0xFF0277BD)),
                center = center,
                radius = 24.dp.toPx()
            ),
            radius = 22.dp.toPx(),
            center = center
        )
        // Silver border
        drawCircle(
            color = Color(0xFFE1F5FE).copy(alpha = 0.7f),
            radius = 22.dp.toPx(),
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Stylized Car front
        val carTop = center.y - 8.dp.toPx()
        val carBottom = center.y + 10.dp.toPx()
        val carLeft = center.x - 14.dp.toPx()
        val carRight = center.x + 14.dp.toPx()

        // Roof & windshield
        val path = Path().apply {
            moveTo(center.x - 8.dp.toPx(), carTop)
            lineTo(center.x + 8.dp.toPx(), carTop)
            lineTo(carRight, carTop + 8.dp.toPx())
            lineTo(carRight, carBottom)
            lineTo(carLeft, carBottom)
            lineTo(carLeft, carTop + 8.dp.toPx())
            close()
        }
        drawPath(path, Color.White)

        // Windshield cutout
        val wsPath = Path().apply {
            moveTo(center.x - 6.dp.toPx(), carTop + 2.dp.toPx())
            lineTo(center.x + 6.dp.toPx(), carTop + 2.dp.toPx())
            lineTo(carRight - 2.dp.toPx(), carTop + 7.dp.toPx())
            lineTo(carLeft + 2.dp.toPx(), carTop + 7.dp.toPx())
            close()
        }
        drawPath(wsPath, Color(0xFF01579B))

        // Headlights
        drawCircle(Color(0xFFFFEA00), radius = 2.5.dp.toPx(), center = Offset(carLeft + 3.dp.toPx(), carBottom - 3.dp.toPx()))
        drawCircle(Color(0xFFFFEA00), radius = 2.5.dp.toPx(), center = Offset(carRight - 3.dp.toPx(), carBottom - 3.dp.toPx()))
    }
}

@Composable
fun LogIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val padW = 28.dp.toPx()
        val padH = 34.dp.toPx()
        val padLeft = (size.width - padW) / 2f
        val padTop = (size.height - padH) / 2f

        // Yellow notepad body
        drawRoundRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(padLeft, padTop),
            size = Size(padW, padH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        // Red top binding
        drawRoundRect(
            color = Color(0xFFD32F2F),
            topLeft = Offset(padLeft, padTop),
            size = Size(padW, 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
        // Ruling lines
        for (i in 1..4) {
            val y = padTop + 8.dp.toPx() + i * 5.dp.toPx()
            drawLine(
                color = Color(0xFF795548).copy(alpha = 0.5f),
                start = Offset(padLeft + 4.dp.toPx(), y),
                end = Offset(padLeft + padW - 4.dp.toPx(), y),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        // Pencil diagonally across
        drawLine(
            color = Color(0xFF1E88E5),
            start = Offset(padLeft + padW + 2.dp.toPx(), padTop + 2.dp.toPx()),
            end = Offset(padLeft + padW - 14.dp.toPx(), padTop + padH - 4.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ErrorsIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val top = cy - 16.dp.toPx()
        val bottom = cy + 16.dp.toPx()
        val halfBase = 18.dp.toPx()

        // Equilateral warning triangle with rounded corners
        val path = Path().apply {
            moveTo(cx, top)
            lineTo(cx + halfBase, bottom)
            lineTo(cx - halfBase, bottom)
            close()
        }

        // Fill safety yellow
        drawPath(path, Color(0xFFFFC107))
        // Black outline
        drawPath(path, Color(0xFF212121), style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))

        // Exclamation mark '!'
        drawLine(
            color = Color(0xFF212121),
            start = Offset(cx, cy - 6.dp.toPx()),
            end = Offset(cx, cy + 5.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFF212121),
            radius = 2.dp.toPx(),
            center = Offset(cx, cy + 10.5.dp.toPx())
        )
    }
}

@Composable
fun DashboardIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = 21.dp.toPx()

        // Outer chrome ring
        drawCircle(Color(0xFF90A4AE), radius = r + 2.dp.toPx(), center = center)
        // Black dial face
        drawCircle(Color(0xFF1A1C20), radius = r, center = center)

        // Green eco-arc
        drawArc(
            color = Color(0xFF00E676),
            startAngle = 150f,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx())
        )
        // Red high-rev arc
        drawArc(
            color = Color(0xFFFF5252),
            startAngle = 270f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx())
        )

        // Red needle pointing ~70%
        val needleRad = Math.toRadians(300.0)
        val tip = Offset(
            (center.x + (r - 4.dp.toPx()) * cos(needleRad)).toFloat(),
            (center.y + (r - 4.dp.toPx()) * sin(needleRad)).toFloat()
        )
        drawLine(Color(0xFFFF3D00), center, tip, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 3.dp.toPx(), center = center)
    }
}

@Composable
fun TableIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val w = 34.dp.toPx()
        val h = 26.dp.toPx()
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f

        // Table background
        drawRoundRect(
            color = Color(0xFF263238),
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )
        // Cyan header
        drawRoundRect(
            color = Color(0xFF00B0FF),
            topLeft = Offset(left, top),
            size = Size(w, 7.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
        // Grid lines
        drawLine(Color(0xFF546E7A), Offset(left + w * 0.45f, top), Offset(left + w * 0.45f, top + h), strokeWidth = 1.dp.toPx())
        drawLine(Color(0xFF546E7A), Offset(left, top + 13.dp.toPx()), Offset(left + w, top + 13.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(Color(0xFF546E7A), Offset(left, top + 19.dp.toPx()), Offset(left + w, top + 19.dp.toPx()), strokeWidth = 1.dp.toPx())

        // Small indicator cells
        drawCircle(Color(0xFF00E676), radius = 1.5.dp.toPx(), center = Offset(left + 6.dp.toPx(), top + 16.dp.toPx()))
        drawCircle(Color(0xFFFFB300), radius = 1.5.dp.toPx(), center = Offset(left + 6.dp.toPx(), top + 22.dp.toPx()))
    }
}

@Composable
fun GraphIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val w = 34.dp.toPx()
        val h = 28.dp.toPx()
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f

        // Dark oscilloscope screen
        drawRoundRect(
            color = Color(0xFF0A140F),
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        // Green grid
        for (i in 1..3) {
            val y = top + (i / 4f) * h
            drawLine(Color(0xFF1B5E20).copy(alpha = 0.5f), Offset(left, y), Offset(left + w, y), strokeWidth = 0.8.dp.toPx())
        }
        for (i in 1..4) {
            val x = left + (i / 5f) * w
            drawLine(Color(0xFF1B5E20).copy(alpha = 0.5f), Offset(x, top), Offset(x, top + h), strokeWidth = 0.8.dp.toPx())
        }

        // Oscilloscope Waveform (Neon Green Sine Curve)
        val wave = Path().apply {
            moveTo(left + 2.dp.toPx(), top + h * 0.5f)
            lineTo(left + w * 0.25f, top + h * 0.5f)
            lineTo(left + w * 0.35f, top + 4.dp.toPx())
            lineTo(left + w * 0.5f, top + h - 5.dp.toPx())
            lineTo(left + w * 0.65f, top + 8.dp.toPx())
            lineTo(left + w * 0.75f, top + h * 0.5f)
            lineTo(left + w - 2.dp.toPx(), top + h * 0.5f)
        }
        drawPath(wave, Color(0xFF00E676), style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
fun TestsIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val padW = 28.dp.toPx()
        val padH = 34.dp.toPx()
        val padLeft = (size.width - padW) / 2f
        val padTop = (size.height - padH) / 2f

        // Clipboard board
        drawRoundRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(padLeft - 2.dp.toPx(), padTop - 2.dp.toPx()),
            size = Size(padW + 4.dp.toPx(), padH + 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        // Sheet of paper
        drawRoundRect(
            color = Color(0xFFECEFF1),
            topLeft = Offset(padLeft, padTop),
            size = Size(padW, padH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
        // Metallic Clip at top
        drawRoundRect(
            color = Color(0xFFB0BEC5),
            topLeft = Offset(padLeft + padW * 0.25f, padTop - 4.dp.toPx()),
            size = Size(padW * 0.5f, 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
        // Checklist lines
        for (i in 1..3) {
            val y = padTop + 8.dp.toPx() + i * 6.dp.toPx()
            drawCircle(Color(0xFF37474F), radius = 1.5.dp.toPx(), center = Offset(padLeft + 5.dp.toPx(), y))
            drawLine(Color(0xFF78909C), Offset(padLeft + 9.dp.toPx(), y), Offset(padLeft + padW - 4.dp.toPx(), y), strokeWidth = 1.5.dp.toPx())
        }
        // Big green checkmark
        val check = Path().apply {
            moveTo(padLeft + 14.dp.toPx(), padTop + padH - 9.dp.toPx())
            lineTo(padLeft + 18.dp.toPx(), padTop + padH - 4.dp.toPx())
            lineTo(padLeft + 27.dp.toPx(), padTop + padH - 15.dp.toPx())
        }
        drawPath(check, Color(0xFF00C853), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ServicesIconGraphic() {
    Canvas(modifier = Modifier.size(52.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = 21.dp.toPx()

        // Circular blue medallion
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF1976D2), Color(0xFF0D47A1)),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
        drawCircle(
            color = Color(0xFF90CAF9).copy(alpha = 0.7f),
            radius = r,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Crossed tools (Silver Wrench & Screwdriver)
        // Wrench line
        val p1 = Offset(center.x - 11.dp.toPx(), center.y + 11.dp.toPx())
        val p2 = Offset(center.x + 11.dp.toPx(), center.y - 11.dp.toPx())
        drawLine(Color(0xFFECEFF1), p1, p2, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

        // Screwdriver line
        val p3 = Offset(center.x + 11.dp.toPx(), center.y + 11.dp.toPx())
        val p4 = Offset(center.x - 11.dp.toPx(), center.y - 11.dp.toPx())
        drawLine(Color(0xFFFFB300), p3, p4, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

        // Center rivet
        drawCircle(Color(0xFF37474F), radius = 2.5.dp.toPx(), center = center)
    }
}
