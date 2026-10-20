package com.xstar.guitar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.media.AudioManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xstar.guitar.audio.*
import com.xstar.guitar.data.LessonProgressEntity
import com.xstar.guitar.data.XStarDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class RailPreset(val title: String, val strings: List<GuitarString>) {
    Standard("标准调弦", GuitarTuning.strings),
    DropD("Drop D", listOf(GuitarString(6, "D2", 73.4162)) + GuitarTuning.strings.drop(1)),
    OpenG("Open G", listOf(GuitarString(6,"D2",73.4162), GuitarString(5,"G2",97.9989), GuitarString(4,"D3",146.8324), GuitarString(3,"G3",195.9977), GuitarString(2,"B3",246.9417), GuitarString(1,"D4",293.6648)))
}

@Composable
fun TunerScreen(database: XStarDatabase, onClose: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context as? android.app.Activity
        val previous = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose { if (previous != null) activity.volumeControlStream = previous }
    }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val tuner = remember { AudioTuner(context.applicationContext, scope) }
    val reading by tuner.reading.collectAsState()
    val error by tuner.error.collectAsState()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(6) }
    var preset by remember { mutableStateOf(RailPreset.Standard) }
    var completed by remember { mutableStateOf(setOf<Int>()) }
    var stable by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var reference by remember { mutableIntStateOf(440) }
    var sounding by remember { mutableStateOf(false) }
    var toneJob by remember { mutableStateOf<Job?>(null) }
    var pluck by remember { mutableIntStateOf(0) }
    var toneError by remember { mutableStateOf<String?>(null) }
    var candidateNumber by remember { mutableIntStateOf(selected) }
    var candidateFrames by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; asked = true
        if (it && !sounding && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tuner.start()
    }
    LaunchedEffect(Unit) { if (granted) tuner.start() else permission.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(owner, tuner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted && !sounding) tuner.start()
            } else if (event == Lifecycle.Event.ON_PAUSE) { toneJob?.cancel(); sounding = false; tuner.stop() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); toneJob?.cancel(); tuner.stop() }
    }
    val target = preset.strings.first { it.number == selected }
    val cents = reading?.let { GuitarTuning.cents(it.frequency.toDouble(), target, reference) }
    LaunchedEffect(reading, manual, preset, sounding, reference) {
        if (!manual && !sounding) reading?.let { sample ->
            val candidate = preset.strings.minBy { abs(GuitarTuning.cents(sample.frequency.toDouble(), it, reference)) }.number
            if (candidate == candidateNumber) candidateFrames++ else { candidateNumber = candidate; candidateFrames = 1 }
            if (candidate == selected || candidateFrames >= 3) {
                if (candidate != selected) { selected = candidate; stable = false }
                candidateFrames = 0
            }
        }
    }
    LaunchedEffect(target, reference, sounding) {
        stable = false
        var matchingFrames = 0
        tuner.reading.collect { sample ->
            val matching = !sounding && sample != null && abs(GuitarTuning.cents(sample.frequency.toDouble(), target, reference)) <= 5
            if (!matching) { matchingFrames = 0; stable = false }
            else {
                matchingFrames++
                // AudioTuner emits roughly 8–12 readings per second. Requiring
                // several consecutive frames avoids false positives while
                // guaranteeing the completed marker is persisted in Compose state.
                if (matchingFrames >= 6) {
                    stable = true
                    completed = completed.toMutableSet().apply { add(target.number) }
                }
            }
        }
    }
    LaunchedEffect(completed.size) {
        if (completed.size == 6) database.progressDao().saveLesson(LessonProgressEntity("tuning", "COMPLETED"))
    }
    fun choose(number: Int) {
        Log.d("XStarReference", "Selected string $number")
        selected = number; pluck++; toneError = null
        val previous = toneJob
        previous?.cancel(); tuner.stop(); sounding = true
        toneJob = scope.launch {
            previous?.join()
            try { ReferenceTone.play(preset.strings.first { it.number == number }.frequency * reference / 440); Log.d("XStarReference", "Playback completed") }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { Log.e("XStarReference", "Playback failed", failure); toneError = "参考音播放失败，请重试" }
            if (toneJob == coroutineContext[Job]) {
                sounding = false
                delay(250)
                if (granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tuner.start()
            }
        }
    }
    TunerTheme {
        Surface(Modifier.fillMaxSize(), color = TunerStyle.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        TextButton({ presetMenu = true }) { Text(preset.title + " ⌄", fontSize = 11.sp, color = TunerStyle.secondary) }
                        DropdownMenu(presetMenu, { presetMenu = false }) {
                            RailPreset.entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.title) }, onClick = {
                                toneJob?.cancel(); sounding = false; preset = entry; completed = emptySet(); presetMenu = false
                                if (granted) tuner.start()
                            }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("标准音与进度设置") }, onClick = { presetMenu = false; settings = true })
                        }
                    }
                    TextButton({ manual = !manual }, Modifier.align(Alignment.Center)) { Text(if (manual) "手动选弦 ⌄" else "自动识别 ⌄", fontSize = 11.sp) }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compact = maxHeight < 340.dp
                    SixStringRails(selected, cents, stable, sounding, pluck, completed, Modifier.fillMaxSize(), ::choose)
                    Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .06f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("第 ${target.number} 弦" + if (target.number == 6) " · 低音" else if (target.number == 1) " · 高音" else "", fontSize = 11.sp, color = TunerStyle.secondary.copy(alpha = .65f))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(target.note.dropLast(1), fontSize = if (compact) 76.sp else 104.sp, lineHeight = if (compact) 82.sp else 112.sp, fontWeight = FontWeight.Light, color = TunerStyle.ink.copy(alpha = .4f))
                            Text(target.note.takeLast(1), fontSize = 22.sp, color = TunerStyle.secondary.copy(alpha = .65f), modifier = Modifier.padding(start = 5.dp, bottom = 12.dp))
                        }
                    }
                    if (!granted || error != null || toneError != null) {
                        TextButton({
                            toneError = null
                            if (granted) tuner.start()
                            else if (asked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            else permission.launch(Manifest.permission.RECORD_AUDIO)
                        }, Modifier.align(Alignment.BottomCenter)) { Text(toneError ?: if (!granted) "开启麦克风" else "重试监听", fontSize = 12.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
                    preset.strings.forEach { string ->
                        val active = selected == string.number
                        Column(Modifier.weight(1f).height(72.dp).background(if (active) TunerStyle.accent.copy(alpha = .06f) else Color.Transparent).clickable { choose(string.number) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(if (active) 2.dp else 1.dp).background(if (active) TunerStyle.accent else TunerStyle.outline))
                            Spacer(Modifier.height(10.dp))
                            Text("${string.number}", fontSize = 9.sp, color = TunerStyle.secondary)
                            Text(string.note.dropLast(1), fontSize = 22.sp, color = if (active) TunerStyle.accent else TunerStyle.secondary)
                            if (string.number in completed) Text("•", fontSize = 9.sp, color = TunerStyle.steady)
                        }
                    }
                }
            }
        }
        if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("调音设置") }, text = {
            Column {
                Text("A4 标准音：$reference Hz")
                Slider(reference.toFloat(), { toneJob?.cancel(); sounding = false; reference = it.toInt(); completed = emptySet(); if (granted) tuner.start() }, valueRange = 430f..450f, steps = 19)
                Text("已调准 ${completed.size} / 6 根弦", color = TunerStyle.secondary)
                TextButton({ completed = emptySet() }) { Text("重置进度") }
            }
        }, confirmButton = { TextButton({ settings = false }) { Text("完成") } })
    }
}
