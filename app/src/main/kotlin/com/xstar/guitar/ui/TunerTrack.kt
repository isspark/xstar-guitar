package com.xstar.guitar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TunerTrack(cents: Float?, stable: Boolean) {
    val position by animateFloatAsState((cents ?: 0f).coerceIn(-50f, 50f), tween(160), label = "精密指针")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val light = if (stable) LocalXStarColors.current.steady else MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(124.dp).semantics { contentDescription = if (cents == null) "等待拨弦" else "偏差 ${cents.toInt()} 音分${if (stable) "，已调准" else ""}" }) {
        val center = Offset(size.width / 2, size.height + size.width * .28f)
        val radius = size.width * .60f
        fun point(degrees: Float, r: Float): Offset { val a = Math.toRadians(degrees.toDouble()); return Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r) }
        drawArc(muted.copy(alpha = .16f), 225f, 90f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(1.dp.toPx()))
        for (i in 0..40) {
            val angle = 225f + i * 2.25f
            val length = (if (i == 20) 19 else if (i % 5 == 0) 13 else 6).dp.toPx()
            drawLine(if (i == 20) light else muted.copy(alpha = if (i % 5 == 0) .7f else .3f), point(angle, radius + 5.dp.toPx()), point(angle, radius + length), (if (i == 20) 2 else 1).dp.toPx(), StrokeCap.Round)
        }
        if (cents != null) {
            val angle = 270 + position * .9f
            val tip = point(angle, radius - 6.dp.toPx())
            drawLine(light, point(angle, radius - 45.dp.toPx()), tip, 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(light.copy(alpha = .3f), Color.Transparent), tip, 11.dp.toPx()), 11.dp.toPx(), tip)
            drawCircle(light, 3.dp.toPx(), tip)
        }
    }
}
