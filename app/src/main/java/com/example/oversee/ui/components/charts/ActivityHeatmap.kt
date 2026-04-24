package com.example.oversee.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.util.*

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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("12 AM", fontSize = 10.sp, color = Color.Gray)
                Text("6 AM", fontSize = 10.sp, color = Color.Gray)
                Text("12 PM", fontSize = 10.sp, color = Color.Gray)
                Text("6 PM", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}