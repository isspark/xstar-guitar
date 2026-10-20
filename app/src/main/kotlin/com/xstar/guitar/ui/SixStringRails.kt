package com.xstar.guitar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SixStringRails(selected: Int, cents: Float?, stable: Boolean, sounding: Boolean, pluck: Int, completed: Set<Int>, modifier: Modifier, onSelect: (Int) -> Unit) {
    val transition = rememberInfiniteTransition(label = "string light")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "flow")
    val breath by transition.animateFloat(.45f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breath")
    val impulse = remember { Animatable(0f) }
    LaunchedEffect(pluck) { if (pluck > 0) { impulse.snapTo(1f); impulse.animateTo(0f, tween(650)) } }
    val deviation by animateFloatAsState((cents ?: 0f).coerceIn(-50f, 50f), tween(180), label = "pitch")
    val accent = TunerStyle.accent
    val dot = if (stable) TunerStyle.steady else accent
    val labelSize = with(LocalDensity.current) { 9.sp.toPx() }
    val labelPaint = remember(labelSize) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = labelSize
            typeface = android.graphics.Typeface.MONOSPACE
            color = android.graphics.Color.rgb(154, 137, 128)
        }
    }
    val hasPitch = cents != null && !sounding
    val currentSelect by rememberUpdatedState(onSelect)
    Canvas(modifier.semantics { contentDescription = "六弦音准光轨。" + if (hasPitch) {
        if (stable) "已调准" else "偏差 ${cents!!.toInt()} 音分，下方偏低、上方偏高"
    } else "等待琴弦声音，空心圆为标准音位置" }.pointerInput(Unit) {
        detectTapGestures { point -> currentSelect(6 - (point.x / size.width * 6).toInt().coerceIn(0, 5)) }
    }) {
        val center = size.height * .52f
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = .035f * breath), Color.Transparent), Offset(size.width / 2, center), size.width * .8f))
        for (index in 0..5) {
            val number = 6 - index
            val x = size.width * (index + .5f) / 6
            val active = number == selected
            val width = (.45f + (5 - index) * .06f).dp.toPx()
            val amplitude = if (active) impulse.value * 5.dp.toPx() + if (sounding) 1.dp.toPx() else 0f else 0f
            val path = Path()
            for (step in 0..70) {
                val y = size.height * step / 70
                val offset = sin(step / 70.0 * PI) * sin(step * .5 + phase * 100) * amplitude
                if (step == 0) path.moveTo(x, y) else path.lineTo(x + offset.toFloat(), y)
            }
            val gradient = Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = if (active) .23f else .065f), accent.copy(alpha = if (active) .32f else .05f), Color.Transparent))
            if (active) drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = .025f * breath), Color.Transparent)), style = Stroke(4.dp.toPx()))
            drawPath(path, gradient, style = Stroke(width))
            if (number in completed && !active) drawCircle(TunerStyle.steady.copy(alpha = .4f), 2.dp.toPx(), Offset(x, center))
            if (active) {
                val flow = if (sounding) (phase * 3) % 1f else phase
                val y = size.height * (1 - flow)
                drawLine(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = .7f), Color.Transparent), y - 14.dp.toPx(), y + 18.dp.toPx()), Offset(x, y - 14.dp.toPx()), Offset(x, y + 18.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                // Decorative light is a narrow streak, never a second pitch dot.
                drawLine(Color(0xFFFFD6B9).copy(alpha = .8f), Offset(x, y - 3.dp.toPx()), Offset(x, y + 3.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
                val side = if (index == 5) -1f else 1f
                for (tick in listOf(25, 0, -25)) {
                    val tickY = center - tick / 50f * size.height * .32f
                    drawLine(accent.copy(alpha = if (tick == 0) .55f else .25f), Offset(x + side * 10.dp.toPx(), tickY), Offset(x + side * 15.dp.toPx(), tickY), .7.dp.toPx())
                    labelPaint.textAlign = if (side > 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                    labelPaint.alpha = if (tick == 0) 200 else 135
                    drawContext.canvas.nativeCanvas.drawText(if (tick > 0) "+$tick" else if (tick < 0) "−${-tick}" else "0", x + side * 19.dp.toPx(), tickY + labelSize * .35f, labelPaint)
                }
                drawCircle(accent.copy(alpha = .65f), 8.dp.toPx(), Offset(x, center), style = Stroke(1.dp.toPx()))
                // Android Y grows downward: negative cents (flat/low) go
                // below the target; positive cents (sharp/high) go above it.
                val position = Offset(x, center - deviation / 50 * size.height * .32f)
                if (hasPitch) {
                    drawCircle(Brush.radialGradient(listOf(dot.copy(alpha = .45f * breath), Color.Transparent), position, 20.dp.toPx()), 20.dp.toPx(), position)
                    drawCircle(dot, 4.dp.toPx(), position)
                    drawCircle(Color.White.copy(alpha = .9f), 1.6.dp.toPx(), position)
                }
                if (impulse.value > .02f) drawCircle(accent.copy(alpha = impulse.value * .5f), (1 - impulse.value) * 35.dp.toPx() + 4.dp.toPx(), Offset(x, center), style = Stroke(1.dp.toPx()))
                if (stable && hasPitch) drawCircle(dot.copy(alpha = breath * .4f), 10.dp.toPx(), position, style = Stroke(1.dp.toPx()))
            }
        }
    }
}
