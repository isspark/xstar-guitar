package com.xstar.guitar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.xstar.guitar.R
import com.xstar.guitar.audio.GuitarTuning

@Composable
fun GuitarHeadstock(current: Int?, completed: Set<Int>, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val steady = LocalXStarColors.current.steady
    val silver = Color(0xFFBAC4CE)
    BoxWithConstraints(Modifier.widthIn(max = 304.dp).fillMaxWidth().aspectRatio(320f / 340f)) {
        Image(painterResource(R.drawable.tuner_headstock), null, Modifier.fillMaxSize())
        Canvas(Modifier.matchParentSize()) {
            val sx = size.width / 320f
            val sy = size.height / 340f
            listOf(4 to 3, 5 to 2, 6 to 1).forEachIndexed { row, pair ->
                val y = (88 + row * 80) * sy
                listOf(pair.first to true, pair.second to false).forEach { (number, left) ->
                    val x = (if (left) 106 else 214) * sx
                    val keyX = (if (left) 62 else 258) * sx
                    val post = Offset(x, y)
                    val color = if (current == number) accent else if (number in completed) steady else silver
                    drawLine(Color(0xFF626C77), Offset(keyX, y), post, 6 * sx, StrokeCap.Round)
                    drawOval(Brush.verticalGradient(listOf(Color(0xFF555E66), Color(0xFFE3E7EA), Color(0xFF87929C), Color(0xFF424B54)), y - 17 * sy, y + 17 * sy), Offset(keyX - 13 * sx, y - 17 * sy), Size(26 * sx, 34 * sy))
                    drawLine(Color.White.copy(alpha = .55f), Offset(keyX - 6 * sx, y - 12 * sy), Offset(keyX + 6 * sx, y - 12 * sy), sx)
                    drawCircle(Color(0xFF59636C), 11 * sx, post)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFE7ECEF), silver, Color(0xFF5A646E)), post, 9 * sx), 9 * sx, post)
                    drawCircle(Color(0xFF3D454C), 2 * sx, post)
                    val nutX = (124 + (6 - number) * 14.4f) * sx
                    drawLine(color.copy(alpha = if (current == number) .95f else .65f), Offset(nutX, size.height), post, (if (number >= 4) 1.5f else .9f) * sx, StrokeCap.Round)
                    if (current == number) drawOval(accent.copy(alpha = .9f), Offset(keyX - 19 * sx, y - 23 * sy), Size(38 * sx, 46 * sy), style = Stroke(1.4f * sx))
                    if (number in completed) drawCircle(steady, 2.5f * sx, Offset(keyX, y + 24 * sy))
                }
            }
        }
        listOf(4 to 3, 5 to 2, 6 to 1).forEachIndexed { row, pair ->
            listOf(pair.first to true, pair.second to false).forEach { (number, left) ->
                val note = GuitarTuning.strings.first { it.number == number }.note
                Box(Modifier.offset(x = maxWidth * (if (left) .1f else .71f), y = maxHeight * ((88 + row * 80 - 28) / 340f))
                    .width(maxWidth * .19f).height(maxHeight * (56f / 340f)).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "第 $number 弦 $note"; stateDescription = "${if (current == number) "当前目标，" else ""}${if (number in completed) "已调准" else "待调音"}" }
                    .clickable(role = Role.Button) { onSelect(number) })
                Text(note, color = if (current == number) accent else TunerStyle.secondary, fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = if (current == number) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).offset(y = maxHeight * ((88 + row * 80 - 10) / 340f)))
            }
        }
    }
}
