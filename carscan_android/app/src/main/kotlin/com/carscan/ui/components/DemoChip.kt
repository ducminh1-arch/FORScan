package com.carscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DemoChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFFB300).copy(alpha = 0.2f))
            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "DEMO",
            color = Color(0xFFFFB300),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
