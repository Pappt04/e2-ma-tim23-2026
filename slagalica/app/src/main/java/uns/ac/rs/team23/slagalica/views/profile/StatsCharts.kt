package uns.ac.rs.team23.slagalica.views.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ChartSegment(
    val label: String,
    val value: Float,
    val color: Color,
)

@Composable
fun PieChartCard(
    title: String,
    segments: List<ChartSegment>,
    modifier: Modifier = Modifier,
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    val filtered = segments.filter { it.value > 0f }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                var startAngle = -90f
                filtered.forEach { seg ->
                    val sweep = seg.value / total * 360f
                    drawArc(
                        color = seg.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                    )
                    startAngle += sweep
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filtered.forEach { seg ->
                    LegendRow(
                        color = seg.color,
                        label = seg.label,
                        value = "${(seg.value / total * 100f).toInt()}%",
                    )
                }
            }
        }
    }
}

@Composable
fun HorizontalPercentBars(
    title: String,
    bars: List<ChartSegment>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        bars.forEach { bar ->
            PercentBarRow(
                label = bar.label,
                percent = bar.value.coerceIn(0f, 100f),
                color = bar.color,
            )
        }
    }
}

@Composable
fun HorizontalValueBars(
    title: String,
    bars: List<ChartSegment>,
    modifier: Modifier = Modifier,
) {
    val maxAbs = bars.maxOfOrNull { kotlin.math.abs(it.value) }?.coerceAtLeast(1f) ?: 1f
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        bars.forEach { bar ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.32f),
                    maxLines = 2,
                )
                Canvas(
                    modifier = Modifier
                        .weight(0.48f)
                        .height(12.dp),
                ) {
                    drawRect(color = trackColor, size = size)
                    val width = size.width * (kotlin.math.abs(bar.value) / maxAbs)
                    drawRect(color = bar.color, size = Size(width, size.height))
                }
                Text(
                    text = if (bar.value % 1f == 0f) bar.value.toInt().toString() else "%.1f".format(bar.value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.2f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
fun VerticalBarChart(
    title: String,
    bars: List<ChartSegment>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
) {
    val peak = maxValue ?: bars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (bar.value % 1f == 0f) bar.value.toInt().toString() else "%.1f".format(bar.value),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Canvas(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(width = 28.dp, height = 100.dp),
                    ) {
                        val barHeight = size.height * (bar.value / peak)
                        drawRect(
                            color = bar.color,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(size.width, barHeight),
                        )
                    }
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PercentBarRow(label: String, percent: Float, color: Color) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${percent.toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(10.dp),
        ) {
            drawRect(color = trackColor, size = size)
            drawRect(
                color = color,
                size = Size(size.width * percent / 100f, size.height),
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text("$label  $value", style = MaterialTheme.typography.bodySmall)
    }
}
