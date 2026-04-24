package com.example.oversee.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle

// --- PREVIEW IMPORTS ---
import androidx.compose.ui.tooling.preview.Preview
import com.example.oversee.utils.MockData

@Composable
fun TrendLineChart(
    incidents: List<FirebaseSyncManager.LogEntry>,
    startDate: Long,
    endDate: Long,
    initialSelectedIndex: Int? = null // NEW: Allows previews to force the tooltip open
) {
    val dayInMillis = 24 * 60 * 60 * 1000L
    val daysCount = remember(startDate, endDate) { maxOf(1, ((endDate - startDate) / dayInMillis).toInt() + 1) }

    val dailyCounts = remember(incidents, startDate, endDate, daysCount) {
        val counts = IntArray(daysCount) { 0 }
        incidents.forEach { incident ->
            if (incident.timestamp in startDate..endDate) {
                val daysFromStart = ((incident.timestamp - startDate) / dayInMillis).toInt()
                if (daysFromStart in 0 until daysCount) {
                    counts[daysFromStart]++
                }
            }
        }
        counts
    }

    val maxCount = dailyCounts.maxOrNull()?.coerceAtLeast(1) ?: 1

    // --- INTERACTIVE STATE ---
    // Uses the passed initial value so previews can show the tooltip!
    var selectedIndex by remember { mutableStateOf(initialSelectedIndex) }
    val textMeasurer = rememberTextMeasurer()
    val tooltipDateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp), // Slightly taller to fit tooltips
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        shape = RoundedCornerShape(AppTheme.CardCorner),
        border = BorderStroke(1.dp, AppTheme.Border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // --- GESTURE DETECTION ---
                    .pointerInput(daysCount) {
                        detectTapGestures(
                            onPress = { offset ->
                                val widthPerBar = size.width / daysCount
                                val index = (offset.x / widthPerBar).toInt().coerceIn(0, daysCount - 1)
                                selectedIndex = index
                                tryAwaitRelease()
                                selectedIndex = null // Hide tooltip when finger lifts
                            }
                        )
                    }
                    .pointerInput(daysCount) {
                        detectDragGestures(
                            onDragEnd = { selectedIndex = null },
                            onDragCancel = { selectedIndex = null }
                        ) { change, _ ->
                            val widthPerBar = size.width / daysCount
                            val index = (change.position.x / widthPerBar).toInt().coerceIn(0, daysCount - 1)
                            selectedIndex = index
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val widthPerBar = size.width / daysCount
                    val barWidth = (widthPerBar * 0.6f).coerceAtMost(24.dp.toPx())
                    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    val maxBarHeight = size.height - 12.dp.toPx()
                    val points = mutableListOf<Offset>()

                    // 1. Draw Bars
                    for (i in 0 until daysCount) {
                        val count = dailyCounts[i]
                        val heightRatio = count.toFloat() / maxCount.toFloat()
                        val barHeight = (heightRatio * maxBarHeight).coerceAtLeast(4.dp.toPx())
                        val centerX = (i * widthPerBar) + (widthPerBar / 2)
                        val topLeftX = centerX - (barWidth / 2)
                        val topLeftY = size.height - barHeight

                        drawRoundRect(
                            color = if (count > 0) Color(0xFFBBDEFB) else Color(0xFFF5F5F5),
                            topLeft = Offset(topLeftX, topLeftY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = cornerRadius
                        )
                        points.add(Offset(centerX, topLeftY))
                    }

                    // 2. Draw Trend Line
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }

                    drawPath(
                        path = path,
                        color = AppTheme.Primary,
                        style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                    )

                    // Draw Intersection Dots
                    if (daysCount <= 14) {
                        points.forEach { point ->
                            drawCircle(color = AppTheme.Primary, radius = 5.dp.toPx(), center = point)
                            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = point)
                        }
                    }

                    // --- DRAW INTERACTIVE SCRUBBER & TOOLTIP ---
                    selectedIndex?.let { index ->
                        val point = points[index]
                        val count = dailyCounts[index]
                        val date = Date(startDate + (index * dayInMillis))
                        val dateString = tooltipDateFormat.format(date)
                        val tooltipText = "$count incidents\n$dateString"

                        // Draw Vertical Tracking Line
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.5f),
                            start = Offset(point.x, 0f),
                            end = Offset(point.x, size.height),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Highlight the selected dot
                        drawCircle(color = AppTheme.Primary, radius = 8.dp.toPx(), center = point)
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = point)

                        // Draw Tooltip Background
                        val textLayoutResult = textMeasurer.measure(
                            text = tooltipText,
                            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                        val tooltipWidth = textLayoutResult.size.width + 24.dp.toPx()
                        val tooltipHeight = textLayoutResult.size.height + 16.dp.toPx()

                        // Keep tooltip on screen
                        var tooltipX = point.x - (tooltipWidth / 2)
                        if (tooltipX < 0) tooltipX = 0f
                        if (tooltipX + tooltipWidth > size.width) tooltipX = size.width - tooltipWidth

                        val tooltipY = point.y - tooltipHeight - 16.dp.toPx()

                        drawRoundRect(
                            color = Color(0xFF424242), // Dark Gray Tooltip
                            topLeft = Offset(tooltipX, tooltipY.coerceAtLeast(0f)),
                            size = Size(tooltipWidth, tooltipHeight),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )

                        // Draw Tooltip Text
                        drawText(
                            textLayoutResult = textLayoutResult,
                            color = Color.White,
                            topLeft = Offset(tooltipX + 12.dp.toPx(), tooltipY.coerceAtLeast(0f) + 8.dp.toPx())
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Dynamic X-Axis Labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (daysCount <= 7) {
                    val dateFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
                    for (i in 0 until daysCount) {
                        val barDate = remember(i) { Date(startDate + (i * dayInMillis)) }
                        Text(text = dateFormat.format(barDate), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                } else {
                    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
                    Text(text = dateFormat.format(Date(startDate)), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(text = dateFormat.format(Date(endDate)), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(showBackground = true, name = "1. Trend Chart (7 Days)")
@Composable
fun TrendLineChart7DayPreview() {
    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - (6 * 24 * 60 * 60 * 1000L)

    Box(modifier = Modifier.padding(16.dp)) {
        TrendLineChart(incidents = MockData.getIncidents(), startDate = sevenDaysAgo, endDate = now)
    }
}

@Preview(showBackground = true, name = "2. Trend Chart (30 Days)")
@Composable
fun TrendLineChart30DayPreview() {
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = now - (29 * 24 * 60 * 60 * 1000L)

    Box(modifier = Modifier.padding(16.dp)) {
        TrendLineChart(incidents = MockData.getIncidents(), startDate = thirtyDaysAgo, endDate = now)
    }
}

@Preview(showBackground = true, name = "3. Trend Chart (Scrubbed Tooltip)")
@Composable
fun TrendLineChartScrubbedPreview() {
    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - (6 * 24 * 60 * 60 * 1000L)

    Box(modifier = Modifier.padding(16.dp)) {
        TrendLineChart(
            incidents = MockData.getIncidents(),
            startDate = sevenDaysAgo,
            endDate = now,
            initialSelectedIndex = 4 // FORCES THE PREVIEW TO SHOW THE TOOLTIP ON DAY 5
        )
    }
}