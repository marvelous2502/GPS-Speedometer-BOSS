package com.example.gpsspeedometer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A simple analog speedometer gauge. Sweeps from -220° to 40° (a 260° arc) across
 * 0..maxSpeed, coloring green -> amber -> red as speed increases.
 */
@Composable
fun SpeedometerGauge(
    speedKmh: Double,
    maxSpeed: Int = 180,
    modifier: Modifier = Modifier
) {
    val sweepAngle = 260f
    val startAngle = 160f // degrees, standard math convention (0 = 3 o'clock, clockwise)
    val fraction = (speedKmh / maxSpeed).coerceIn(0.0, 1.0)

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val strokeWidth = size.minDimension * 0.08f
            val radius = size.minDimension / 2f - strokeWidth
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)

            // Background track
            drawArc(
                color = Color(0xFFE0E0E0),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Colored progress (green -> amber -> red)
            val progressColor = when {
                fraction < 0.5 -> Color(0xFF4CAF50)
                fraction < 0.8 -> Color(0xFFFFA000)
                else -> Color(0xFFE53935)
            }
            drawArc(
                color = progressColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fraction.toFloat(),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Needle
            val needleAngleDeg = startAngle + sweepAngle * fraction.toFloat()
            val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
            val needleLength = radius * 0.85f
            val needleEnd = Offset(
                x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
            )
            drawLine(
                color = Color(0xFF333333),
                start = center,
                end = needleEnd,
                strokeWidth = strokeWidth * 0.3f,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color(0xFF333333), radius = strokeWidth * 0.4f, center = center)
        }

        Text(
            text = "${speedKmh.toInt()}",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
