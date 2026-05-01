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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.util.*
import kotlin.math.roundToInt

private fun formatHour(hour: Int): String {
    return when (hour) {
        0 -> "12 AM"
        in 1..11 -> "$hour AM"
        12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}

@Composable
fun ActivityHeatmap(dailyIncidents: List<FirebaseSyncManager.LogEntry>) {
    val hourlyCounts = remember(dailyIncidents) {
        val counts = IntArray(24) { 0 }
        val calendar = Calendar.getInstance()
        dailyIncidents.forEach { incident ->
            calendar.timeInMillis = incident.timestamp
            counts[calendar.get(Calendar.HOUR_OF_DAY)]++
        }
        counts
    }

    val maxCount = hourlyCounts.maxOrNull()?.coerceAtLeast(1) ?: 1

    var selectedHour by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Card(
        // 2.2f creates a wide banner shape for the heatmap that scales
        modifier = Modifier.fillMaxWidth().aspectRatio(2.2f),
        shape = RoundedCornerShape(AppTheme.CardCorner),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        border = BorderStroke(1.dp, AppTheme.Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Activity Timeline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                val widthPerHour = size.width / 23f
                                selectedHour = (offset.x / widthPerHour).roundToInt().coerceIn(0, 23)
                                tryAwaitRelease()
                                selectedHour = null
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { selectedHour = null },
                            onDragCancel = { selectedHour = null }
                        ) { change, _ ->
                            val widthPerHour = size.width / 23f
                            selectedHour = (change.position.x / widthPerHour).roundToInt().coerceIn(0, 23)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val widthPerHour = size.width / 23f
                    val path = Path()

                    for (i in 0..23) {
                        val count = hourlyCounts[i]
                        val heightRatio = count.toFloat() / maxCount.toFloat()
                        val y = size.height - (heightRatio * size.height)
                        val x = i * widthPerHour

                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            val prevX = (i - 1) * widthPerHour
                            val prevCount = hourlyCounts[i - 1]
                            val prevY = size.height - ((prevCount.toFloat() / maxCount) * size.height)
                            val controlPointX = (prevX + x) / 2
                            path.cubicTo(
                                x1 = controlPointX, y1 = prevY,
                                x2 = controlPointX, y2 = y,
                                x3 = x, y3 = y
                            )
                        }
                    }

                    drawPath(
                        path = path,
                        color = AppTheme.Primary,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw interactive scrubber and tooltip
                    selectedHour?.let { hour ->
                        val x = hour * widthPerHour
                        val count = hourlyCounts[hour]
                        val dotY = size.height - (count.toFloat() / maxCount * size.height)

                        // Vertical tracking line
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Highlight dot on the curve
                        drawCircle(color = AppTheme.Primary, radius = 8.dp.toPx(), center = Offset(x, dotY))
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, dotY))

                        // Tooltip text
                        val label = if (count == 1) "1 detected word" else "$count detected words"
                        val tooltipText = "${formatHour(hour)}\n$label"
                        val textLayoutResult = textMeasurer.measure(
                            text = tooltipText,
                            style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        val tooltipWidth = textLayoutResult.size.width + 20.dp.toPx()
                        val tooltipHeight = textLayoutResult.size.height + 14.dp.toPx()

                        var tooltipX = x - tooltipWidth / 2
                        if (tooltipX < 0f) tooltipX = 0f
                        if (tooltipX + tooltipWidth > size.width) tooltipX = size.width - tooltipWidth
                        val tooltipY = (dotY - tooltipHeight - 12.dp.toPx()).coerceAtLeast(0f)

                        drawRoundRect(
                            color = Color(0xFF424242),
                            topLeft = Offset(tooltipX, tooltipY),
                            size = Size(tooltipWidth, tooltipHeight),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            color = Color.White,
                            topLeft = Offset(tooltipX + 10.dp.toPx(), tooltipY + 7.dp.toPx())
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("12 AM", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.weight(6f))
                Text("6 AM", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.weight(6f))
                Text("12 PM", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.weight(6f))
                Text("6 PM", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.weight(5f))
            }
        }
    }
}
