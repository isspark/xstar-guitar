package com.xstar.guitar.ui

import android.app.Activity
import android.media.AudioManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xstar.guitar.audio.AudioMetronome
import com.xstar.guitar.audio.MetronomeConfig
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val PulseBg get() = ToolPalette.background
private val PulsePanel get() = ToolPalette.panel
private val PulseLine get() = ToolPalette.line
private val PulseInk get() = ToolPalette.ink
private val PulseMuted get() = ToolPalette.muted
private val PulseLime get() = ToolPalette.accent
private val PulseOrange get() = ToolPalette.attention
private val meters = listOf("2/4", "3/4", "4/4", "5/4", "6/8", "7/8")
private val voices = mapOf("electronic" to "电子音", "wood" to "木块音", "soft" to "柔和音")
private val divisions = listOf("♩", "♫", "3 连音", "♬")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(reduceMotion: Boolean = false, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metronome", 0) }
    var config by remember {
        mutableStateOf(runCatching {
            val meter = prefs.getString("meter", "4/4")!!.takeIf { it in meters } ?: "4/4"
            val base = MetronomeConfig().withMeter(meter)
            base.copy(bpm = prefs.getInt("bpm", 120).coerceIn(40, 240), subdivision = prefs.getInt("subdivision", 1).coerceIn(1, 4),
                voice = prefs.getString("voice", "electronic")!!.takeIf { it in voices } ?: "electronic",
                sound = prefs.getBoolean("sound", true), animation = prefs.getBoolean("animation", true), volume = prefs.getFloat("volume", .65f).coerceIn(0f, 1f),
                accents = List(base.beats) { prefs.getInt("accent$it", base.accents[it]).coerceIn(0, 2) })
        }.getOrDefault(MetronomeConfig()))
    }
    val scope = rememberCoroutineScope()
    val engine = remember { AudioMetronome(scope) }
    val beat by engine.beat.collectAsState()
    val error by engine.error.collectAsState()
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var tempoInput by remember { mutableStateOf("") }
    var tapTimes by remember { mutableStateOf(emptyList<Long>()) }
    var tapCount by remember { mutableIntStateOf(0) }
    var hit by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    fun stop() { engine.stop(); running = false }
    fun update(value: MetronomeConfig) { config = value }
    fun reset() { stop(); config = MetronomeConfig(); seconds = 0; tapTimes = emptyList(); tapCount = 0; sheet = null }
    BackHandler { if (sheet != null) sheet = null else { stop(); onClose() } }
    DisposableEffect(owner) {
        val activity = context as? Activity
        val previousStream = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); engine.stop(); if (previousStream != null) activity.volumeControlStream = previousStream }
    }
    LaunchedEffect(config) {
        engine.update(config)
        prefs.edit().putInt("bpm", config.bpm).putString("meter", config.meter).putInt("subdivision", config.subdivision)
            .putString("voice", config.voice).putFloat("volume", config.volume).putBoolean("sound", config.sound).putBoolean("animation", config.animation)
            .apply { config.accents.forEachIndexed { i, level -> putInt("accent$i", level) } }.apply()
    }
    LaunchedEffect(error) { if (error != null) stop() }
    LaunchedEffect(running) {
        if (running) { val start = SystemClock.elapsedRealtime(); seconds = 0; while (true) { delay(200); seconds = ((SystemClock.elapsedRealtime() - start) / 1000).toInt() } }
    }
    LaunchedEffect(beat, running) { hit = running && beat >= 0; if (hit) { delay(120); hit = false } }
    LaunchedEffect(tapTimes) { if (tapTimes.isNotEmpty()) { delay(2000); tapCount = 0 } }
    Surface(Modifier.fillMaxSize(), color = PulseBg) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compact = maxHeight < 700.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).heightIn(min = maxHeight), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        PulseIcon(if (config.sound) "关闭声音" else "开启声音", { update(config.copy(sound = !config.sound)) }) {
                            Icon(if (config.sound) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, null, tint = if (config.sound) PulseLime else PulseMuted)
                        }
                        PulseIcon("节拍器设置", { sheet = "settings" }) { Icon(Icons.Rounded.Tune, null) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text("%02d:%02d".format(seconds / 60, seconds % 60), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = PulseMuted) }
                    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                    Box(Modifier.size(if (compact) 220.dp else 280.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radius = size.minDimension * .45f
                            drawCircle(PulseLine, radius, center, style = Stroke(1.dp.toPx()))
                            repeat(60) { i ->
                                val angle = i * Math.PI / 30
                                val inner = radius * if (i % 5 == 0) .92f else .96f
                                drawLine(if (i % 5 == 0) PulseMuted.copy(alpha = .55f) else PulseLine,
                                    center + Offset((sin(angle) * inner).toFloat(), (-cos(angle) * inner).toFloat()),
                                    center + Offset((sin(angle) * radius * .98).toFloat(), (-cos(angle) * radius * .98).toFloat()), 1.dp.toPx())
                            }
                            val orbit = radius * .85f
                            drawCircle(PulseLime.copy(alpha = if (hit && config.animation && !reduceMotion) .45f else .16f), orbit, center, style = Stroke(1.dp.toPx()))
                            drawArc(PulseLime, -90f + if (beat >= 0) beat * 360f / config.beats else 0f, 50f, false,
                                center - Offset(orbit, orbit), Size(orbit * 2, orbit * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(config.bpm.toString(), fontSize = if (compact) 80.sp else 96.sp, lineHeight = if (compact) 90.sp else 108.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { tempoInput = config.bpm.toString(); sheet = "tempo" }.semantics { contentDescription = "${config.bpm} BPM，点击输入速度" })
                            Text("BPM", fontSize = 11.sp, letterSpacing = 4.sp, color = PulseMuted)
                        }
                    }
                    Slider(config.bpm.toFloat(), { update(config.copy(bpm = it.roundToInt())) }, valueRange = 40f..240f, steps = 199,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "速度 BPM" }, colors = SliderDefaults.colors(thumbColor = PulseLime, activeTrackColor = PulseLime, inactiveTrackColor = PulseLine))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("40", color = PulseMuted, fontSize = 10.sp)
                        Text(when { config.bpm < 76 -> "ADAGIO · 慢速"; config.bpm < 108 -> "ANDANTE · 行板"; config.bpm < 140 -> "MODERATO · 中速"; else -> "ALLEGRO · 快速" }, color = PulseMuted, fontSize = 10.sp)
                        Text("240", color = PulseMuted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 90, 120, 160).forEach { value -> PulseCell(value.toString(), config.bpm == value, Modifier.weight(1f).height(48.dp)) { update(config.copy(bpm = value)) } }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("节拍", fontSize = 12.sp)
                        Text(if (running && beat >= 0) "第 ${beat + 1} 拍 / ${config.beats}" else "${config.beats} BEATS / BAR", color = PulseMuted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        config.accents.forEachIndexed { i, level ->
                            val active = running && beat == i
                            Surface(onClick = { update(config.copy(accents = config.accents.mapIndexed { index, v -> if (index == i) (v + 2) % 3 else v })) },
                                modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = "第 ${i+1} 拍，${listOf("静音", "轻音", "重音")[level]}，点击切换" },
                                shape = RoundedCornerShape(14.dp), color = if (active) PulseLime.copy(alpha = .12f) else PulsePanel,
                                border = BorderStroke(1.dp, if (active) PulseLime else PulseLine)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val color = if (active) PulseLime else if (level == 2) PulseOrange else if (level == 0) PulseMuted else PulseInk
                                    Text((i + 1).toString(), color = color, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                                    Text(listOf("静", "轻", "重")[level], color = color, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple(config.meter, "拍号", "meter"), Triple(divisions[config.subdivision - 1], "细分", "subdivision"), Triple(voices.getValue(config.voice), "音色", "voice")).forEach { (value, title, type) ->
                            Surface(onClick = { sheet = type }, modifier = Modifier.weight(1f).height(64.dp), color = PulsePanel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PulseLine)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(value, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); Text("$title ⌄", color = PulseMuted, fontSize = 10.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compact) 24.dp else 40.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                        OutlinedButton(onClick = {
                            val now = SystemClock.elapsedRealtime()
                            tapTimes = (if (tapTimes.isEmpty() || now - tapTimes.last() > 2000) emptyList() else tapTimes).plus(now).takeLast(7)
                            tapCount = tapTimes.size
                            if (tapTimes.size >= 2) update(config.copy(bpm = (60000.0 * (tapTimes.size - 1) / (now - tapTimes.first()).coerceAtLeast(1)).roundToInt().coerceIn(40, 240)))
                        }, modifier = Modifier.width(88.dp).height(64.dp), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, PulseMuted), contentPadding = PaddingValues(0.dp)) { Text(if (tapCount > 0) "TAP $tapCount" else "TAP", fontSize = 13.sp, letterSpacing = 1.sp) }
                        Button(onClick = { if (running) stop() else { engine.update(config); engine.start(); running = true } }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFF242D1B) else PulseLime, contentColor = if (running) PulseLime else PulseBg)) {
                            Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (running) "停止练习" else "开始练习")
                        }
                    }
                    if (error != null) Text(error!!, color = PulseOrange, fontSize = 12.sp)
                }
            }
        }
        if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = PulsePanel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (sheet) { "settings" -> "练习设置"; "meter" -> "拍号"; "subdivision" -> "节奏细分"; "voice" -> "节拍音色"; else -> "输入速度" }, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    TextButton({ sheet = null }) { Text("关闭") }
                }
                when (sheet) {
                    "settings" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("声音音量", Modifier.width(80.dp)); Slider(config.volume, { update(config.copy(volume = it)) }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("节拍动画", Modifier.weight(1f)); Switch(config.animation, { update(config.copy(animation = it)) }) }
                        OutlinedButton(::reset, Modifier.fillMaxWidth().height(52.dp)) { Text("恢复默认设置") }
                        TextButton({ stop(); onClose() }, Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null); Text("返回工具") }
                    }
                    "meter" -> {
                        Text("6/8、7/8 按八分音符计拍，分别以 3+3、2+2+3 分组重音。", fontSize = 12.sp, color = PulseMuted)
                        meters.chunked(2).forEach { pair -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { value -> PulseCell(value, config.meter == value, Modifier.weight(1f).height(60.dp)) { update(config.withMeter(value)) } } } }
                    }
                    "subdivision" -> (1..4).forEach { value -> PulseCell("${divisions[value-1]} · 每拍${value}声", config.subdivision == value, Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp)) { update(config.copy(subdivision = value)) } }
                    "voice" -> voices.forEach { (value, title) -> PulseCell(title, config.voice == value, Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp)) { update(config.copy(voice = value)) } }
                    "tempo" -> {
                        OutlinedTextField(tempoInput, { tempoInput = it }, label = { Text("40–240 BPM") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        val value = tempoInput.toIntOrNull()
                        Button({ update(config.copy(bpm = value!!)); sheet = null }, enabled = value != null && value in 40..240, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("确认速度") }
                    }
                }
            }
        }
}

@Composable
private fun PulseIcon(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }, shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, PulseLine)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun PulseCell(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), color = if (selected) PulseLime.copy(alpha = .06f) else PulseBg, border = BorderStroke(1.dp, if (selected) PulseLime.copy(alpha = .6f) else PulseLine)) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = if (selected) PulseLime else PulseMuted, fontSize = 13.sp) }
    }
}
