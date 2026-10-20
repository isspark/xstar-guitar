package com.xstar.guitar.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xstar.guitar.audio.AudioMetronome
import com.xstar.guitar.audio.ChordPlayer
import com.xstar.guitar.audio.MetronomeConfig
import com.xstar.guitar.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xstar.guitar.ui.PracticeThemeColors as ToolPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeLibraryScreen(
    database: XStarDatabase,
    onMenu: () -> Unit,
    onChords: () -> Unit,
    onTuner: () -> Unit,
    onSong: (String, String) -> Unit,
    startWithExercise: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("practice", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val history by database.progressDao().observePractice().collectAsState(initial = emptyList())
    var page by rememberSaveable { mutableStateOf(if (startWithExercise) "detail" else "home") }
    var category by rememberSaveable { mutableStateOf("transition") }
    var exerciseId by rememberSaveable { mutableStateOf("em-g") }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var note by rememberSaveable(exerciseId) { mutableStateOf(prefs.getString("note.$exerciseId", "") ?: "") }
    var revision by remember { mutableIntStateOf(0) }
    val favorites = remember(revision) { prefs.getStringSet("favorites", emptySet())!!.toSet() }
    val exercise = PracticeCatalog.find(exerciseId) ?: PracticeCatalog.exercises.first()
    fun open(id: String) { exerciseId = id; page = "detail" }
    fun back() {
        page = when (page) {
            "detail" -> if (startWithExercise) { onClose?.invoke(); "home" } else "category"
            "category", "history" -> "home"
            else -> { onClose?.invoke(); "home" }
        }
    }
    BackHandler(page != "home" && page != "session") { back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (page == "session") {
                PracticeRunner(exercise, database, onExit = { page = "detail" })
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (page != "home" || onClose != null) IconButton({ back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                            Column(Modifier.weight(1f)) {
                                Text("XSTAR", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                Text("GUITAR PRACTICE", color = ToolPalette.muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            if (page == "home") IconButton(onMenu) { Icon(Icons.Rounded.Menu, "打开导航菜单") }
                        }
                    }
                    when (page) {
                        "home" -> {
                            item { Text("Practice room · 练习室", color = ToolPalette.accent, fontSize = 12.sp) }
                            item { Text("今天，练一个\n真正能用的技能。", fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold) }
                            item { PracticeHeading("最近练习", "历史") { page = "history" } }
                            if (history.isEmpty()) item { PracticePanel { Text("还没有练习记录", fontWeight = FontWeight.Bold); Text("从下方选择一个技能，完成后会在这里留下记录。", color = ToolPalette.muted) } }
                            items(history.distinctBy { it.practiceId }.take(3), key = { "recent${it.id}" }) { record ->
                                PracticeRow(PracticeCatalog.find(record.practiceId)?.title ?: record.practiceId,
                                    "${formatDuration(record.durationSeconds)} · ${record.bpm} BPM · ${record.rating}",
                                    if (record.status == "COMPLETED") "复习" else "未完成") { open(record.practiceId) }
                            }
                            item { PracticeHeading("按技能练习", "查看全部") { category = ""; page = "category"; level = ""; filter = "全部" } }
                            items(PracticeCatalog.categories.chunked(2)) { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { cat ->
                                        Surface(onClick = { category = cat.id; page = "category"; level = ""; filter = "全部" },
                                            modifier = Modifier.weight(1f), color = ToolPalette.panel,
                                            shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, ToolPalette.line)) {
                                            Column(Modifier.padding(17.dp)) {
                                                Text(cat.symbol, color = ToolPalette.accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(12.dp)); Text(cat.title, fontWeight = FontWeight.Bold)
                                                Text(cat.description, color = ToolPalette.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                                                val total = PracticeCatalog.exercises.count { it.categoryId == cat.id }
                                                val done = PracticeCatalog.exercises.count { e -> e.categoryId == cat.id && history.any { it.practiceId == e.id && it.status == "COMPLETED" } }
                                                Text("$done / $total 项已练", color = ToolPalette.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        "category" -> {
                            item { Text(PracticeCatalog.categories.find { it.id == category }?.title ?: "全部练习", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
                            item { OutlinedTextField(query, { query = it }, label = { Text("搜索练习、和弦或技巧") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(level.isEmpty(), { level = "" }, { Text("全部难度") })
                                PracticeLevel.entries.forEach { l -> FilterChip(level == l.name, { level = l.name }, { Text(l.label) }) }
                            } }
                            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("全部", "收藏", "已完成", "练习中", "待复习", "未开始").forEach { f -> FilterChip(filter == f, { filter = f }, { Text(f) }) }
                            } }
                            val matching = PracticeCatalog.exercises.filter { e ->
                                (category.isEmpty() || e.categoryId == category) && (level.isEmpty() || e.level.name == level) &&
                                    (e.title.contains(query, true) || e.goal.contains(query, true)) && when (filter) {
                                    "收藏" -> e.id in favorites
                                    "已完成" -> history.any { it.practiceId == e.id && it.status == "COMPLETED" }
                                    "练习中" -> history.any { it.practiceId == e.id } && history.none { it.practiceId == e.id && it.status == "COMPLETED" }
                                    "待复习" -> history.firstOrNull { it.practiceId == e.id }?.let { it.rating == "有点难" || it.completedAt < System.currentTimeMillis() - 7 * 86400000L } == true
                                    "未开始" -> history.none { it.practiceId == e.id }
                                    else -> true
                                }
                            }
                            if (matching.isEmpty()) item { Text("没有符合条件的练习，请调整搜索或筛选。", color = ToolPalette.muted) }
                            items(matching, key = { it.id }) { e -> PracticeRow(e.title, e.summary, e.level.label) { open(e.id) } }
                        }
                        "detail" -> {
                            item { Text("${PracticeCatalog.categories.first { it.id == exercise.categoryId }.title} · ${exercise.level.label}", color = ToolPalette.accent) }
                            item { Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(exercise.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton({ prefs.edit().putStringSet("favorites", if (exercise.id in favorites) favorites - exercise.id else favorites + exercise.id).apply(); revision++ }) {
                                    Text(if (exercise.id in favorites) "★" else "☆", color = ToolPalette.accent, fontSize = 28.sp)
                                }
                            } }
                            item { PracticePanel { Text("今天的目标", color = ToolPalette.accent, fontWeight = FontWeight.Bold); Text(exercise.goal); Text(exercise.summary, color = ToolPalette.muted) } }
                            if (exercise.prerequisites.isNotEmpty()) item { PracticePanel {
                                Text("建议先练（不限制进入）", fontWeight = FontWeight.Bold)
                                exercise.prerequisites.forEach { id -> PracticeCatalog.find(id)?.let { prerequisite ->
                                    TextButton({ open(prerequisite.id) }) { Text(prerequisite.title) }
                                } }
                            } }
                            items(exercise.steps.withIndex().toList()) { (i, step) -> PracticePanel {
                                Text("0${i + 1}  ${step.title}", fontWeight = FontWeight.Bold); Text(step.text, color = ToolPalette.muted, lineHeight = 23.sp)
                            } }
                            item { PracticePanel { Text("怎样算完成", fontWeight = FontWeight.Bold); Text(exercise.check); Text("遇到困难", color = ToolPalette.attention); Text(exercise.tip, color = ToolPalette.muted) } }
                            if (exercise.notes.isNotEmpty() || exercise.chords.isNotEmpty()) item { PracticeDemo(exercise) }
                            item { PracticePanel { Text("应用在哪里", fontWeight = FontWeight.Bold); Text(exercise.application, color = ToolPalette.muted) } }
                            item {
                                OutlinedTextField(note, { value -> note = value; prefs.edit().putString("note.$exerciseId", value).apply() },
                                    label = { Text("我的练习笔记 / 曲单 / 创作草稿") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                            }
                            val records = history.filter { it.practiceId == exercise.id }
                            if (records.isNotEmpty()) item { PracticePanel {
                                Text("练习证据", fontWeight = FontWeight.Bold)
                                Text("${records.size} 次记录 · ${records.count { it.status == "COMPLETED" }} 次完成")
                                val stable = records.filter { it.status == "COMPLETED" && it.rating in listOf("轻松", "刚好") }.maxOfOrNull { it.bpm }
                                if (exercise.mode == PracticeMode.Beat && stable != null) Text("自评稳定速度：$stable BPM", color = ToolPalette.accent)
                                Text("完成记录和自评不等于自动认定掌握。", color = ToolPalette.muted, fontSize = 12.sp)
                            } }
                            item { Button({ page = "session" }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("开始练习") } }
                            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onTuner) { Text("练习前调音") }
                                if (exercise.chords.isNotEmpty()) OutlinedButton(onChords) { Text("查看和弦指法") }
                                exercise.songId?.let { id -> OutlinedButton({ onSong(id, "为你留一盏灯") }) { Text("在曲目中应用") } }
                            } }
                            val related = PracticeCatalog.exercises.filter { it.categoryId == exercise.categoryId && it.id != exercise.id }.take(2)
                            if (related.isNotEmpty()) item { PracticePanel {
                                Text("相关练习", fontWeight = FontWeight.Bold)
                                related.forEach { next -> TextButton({ open(next.id) }) { Text(next.title) } }
                            } }
                        }
                        "history" -> {
                            item { Text("练习历史", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
                            item { Text("${history.size} 次记录 · 总计 ${formatDuration(history.sumOf { it.durationSeconds })}", color = ToolPalette.muted) }
                            if (history.isEmpty()) item { Text("尚无历史记录。完成或保存一次练习后会显示在这里。") }
                            items(history, key = { it.id }) { r -> PracticeRow(
                                PracticeCatalog.find(r.practiceId)?.title ?: r.practiceId,
                                "${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(r.completedAt))}\n${formatDuration(r.durationSeconds)} · ${r.bpm} BPM · ${r.rating}" +
                                    (if (r.problems.isNotBlank()) "\n${r.problems}" else ""),
                                if (r.status == "COMPLETED") "已完成" else "未完成") { open(r.practiceId) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
}

@Composable
private fun PracticeHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick) { Text(action) }
    }
}

@Composable
private fun PracticePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = ToolPalette.panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, ToolPalette.line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun PracticeRow(title: String, subtitle: String, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = ToolPalette.panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, ToolPalette.line)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = ToolPalette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) }
            Text(label, color = ToolPalette.attention, fontSize = 12.sp)
        }
    }
}

private fun demoEvents(e: PracticeExercise): List<PluckEvent> {
    if (e.chords.isNotEmpty()) return e.chords.flatMapIndexed { index, name ->
        val chord = ChordLibrary.chords.first { it.id == name }
        ChordLibrary.sequence(ChordLibrary.voicings(chord).first(), if (e.categoryId == "right") PickingStyle.Pima else PickingStyle.Down)
            .map { it.copy(offsetMillis = it.offsetMillis + index * 2400) }
    }
    return e.notes.mapIndexed { i, note -> PluckEvent(0, note, "音高示范", i * 700) }
}

@Composable
private fun PracticeDemo(exercise: PracticeExercise) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { job?.cancel(); job = null } }
        owner.lifecycle.addObserver(observer)
        onDispose { job?.cancel(); owner.lifecycle.removeObserver(observer) }
    }
    Column {
        OutlinedButton({
            if (job != null) { job?.cancel(); job = null } else {
                error = null
                job = scope.launch {
                    try { ChordPlayer.play(demoEvents(exercise)) {} }
                    catch (c: kotlinx.coroutines.CancellationException) { throw c }
                    catch (_: Exception) { error = "示范声音播放失败，请重试。" }
                    finally { job = null }
                }
            }
        }, Modifier.fillMaxWidth()) { Text(if (job != null) "停止音高示范" else "听音高 / 和弦示范") }
        Text("合成音展示目标音高与顺序；拍弦、滑音等动作请按文字步骤自检。", color = ToolPalette.muted, fontSize = 11.sp)
        error?.let { Text(it, color = ToolPalette.attention) }
    }
}

private fun formatDuration(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeRunner(exercise: PracticeExercise, database: XStarDatabase, onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("practice", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val restored = remember(exercise.id) { runCatching { JSONObject(prefs.getString("active.${exercise.id}", "{}")!!) }.getOrDefault(JSONObject()) }
    var bpm by rememberSaveable { mutableIntStateOf(restored.optInt("bpm", exercise.bpm).coerceIn(40, 200)) }
    var rounds by rememberSaveable { mutableIntStateOf(restored.optInt("rounds", exercise.rounds).coerceIn(1, 32)) }
    var elapsed by rememberSaveable { mutableIntStateOf(restored.optInt("elapsed", 0)) }
    var completedBeats by rememberSaveable { mutableIntStateOf(restored.optInt("beats", 0)) }
    var checks by rememberSaveable { mutableStateOf(restored.optString("checks", "")) }
    var questionIndex by rememberSaveable { mutableIntStateOf(restored.optInt("question", 0).coerceIn(0, 9)) }
    var correct by rememberSaveable { mutableIntStateOf(restored.optInt("correct", 0)) }
    var seed by rememberSaveable { mutableIntStateOf(restored.optInt("seed", kotlin.random.Random.nextInt())) }
    var answer by rememberSaveable { mutableIntStateOf(restored.optInt("answer", -1)) }
    var started by rememberSaveable { mutableStateOf(restored.optBoolean("started", false)) }
    var finished by rememberSaveable { mutableStateOf(restored.optBoolean("finished", false)) }
    var running by remember { mutableStateOf(false) }
    var preBeats by remember { mutableIntStateOf(4) }
    var rating by rememberSaveable { mutableStateOf(restored.optString("rating", "")) }
    var problems by rememberSaveable { mutableStateOf(restored.optString("problems", "")) }
    var heard by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showExit by remember { mutableStateOf(false) }
    var keepAwake by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf(true) }
    var meter by rememberSaveable { mutableStateOf(restored.optString("meter", "4/4")) }
    var subdivision by rememberSaveable { mutableIntStateOf(restored.optInt("subdivision", 1)) }
    var autoIncrease by rememberSaveable { mutableStateOf(restored.optBoolean("autoIncrease", false)) }
    var targetBpm by rememberSaveable { mutableIntStateOf(restored.optInt("targetBpm", 80)) }
    val beatsPerBar = meter.substringBefore('/').toInt()
    var playback by remember { mutableStateOf<Job?>(null) }
    var audioError by remember { mutableStateOf<String?>(null) }
    val engine = remember { AudioMetronome(scope) }
    val beat by engine.beat.collectAsState()
    val engineError by engine.error.collectAsState()
    val owner = LocalLifecycleOwner.current
    val question = remember(exercise.id, seed, questionIndex) { EarTraining.question(exercise.earType, questionIndex, seed) }
    val beatsPerRound = if (exercise.chords.size > 1) exercise.chords.size * beatsPerBar else beatsPerBar
    val totalBeats = rounds * beatsPerRound
    fun snapshot() {
        if (!started) return
        val data = JSONObject().put("bpm", bpm).put("rounds", rounds).put("elapsed", elapsed).put("beats", completedBeats)
            .put("checks", checks).put("question", questionIndex).put("correct", correct).put("seed", seed).put("answer", answer).put("started", true)
            .put("finished", finished).put("rating", rating).put("problems", problems)
            .put("meter", meter).put("subdivision", subdivision).put("autoIncrease", autoIncrease).put("targetBpm", targetBpm)
        prefs.edit().putString("active.${exercise.id}", data.toString()).apply()
    }
    fun pause() { running = false; engine.stop(); playback?.cancel(); playback = null; snapshot() }
    fun save(completed: Boolean) {
        if (saving) return
        pause(); saving = true; saveError = null
        scope.launch {
            try {
                database.progressDao().savePractice(PracticeSessionEntity(
                    practiceId = exercise.id, durationSeconds = elapsed, bpm = bpm,
                    rating = if (exercise.mode == PracticeMode.Ear) "$correct / ${if (completed) 10 else questionIndex + if (answer >= 0) 1 else 0} 正确" else rating.ifBlank { "未自评" },
                    status = if (completed) "COMPLETED" else "INTERRUPTED",
                    completedUnits = when (exercise.mode) { PracticeMode.Ear -> if (completed) 10 else questionIndex; PracticeMode.Guided -> checks.split(',').count { it.isNotBlank() }; else -> completedBeats / 4 },
                    correctAnswers = correct, problems = problems,
                ))
                prefs.edit().remove("active.${exercise.id}").apply(); onExit()
            } catch (_: Exception) { saveError = "保存失败，请重试。"; saving = false }
        }
    }
    BackHandler { pause(); showExit = true }
    val pauseCurrent by rememberUpdatedState(newValue = { pause() })
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) pauseCurrent() }
        owner.lifecycle.addObserver(observer)
        onDispose { engine.stop(); playback?.cancel(); owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(keepAwake, running) {
        val window = (context as? Activity)?.window
        val wasKept = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (keepAwake && running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (!wasKept) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(bpm, sound, meter, subdivision) { engine.update(MetronomeConfig(bpm = bpm, sound = sound, subdivision = subdivision).withMeter(meter)) }
    LaunchedEffect(engineError) { if (engineError != null) pause() }
    LaunchedEffect(running) {
        if (running) {
            val start = SystemClock.elapsedRealtime(); val previous = elapsed
            while (true) { delay(250); elapsed = previous + ((SystemClock.elapsedRealtime() - start) / 1000).toInt() }
        }
    }
    LaunchedEffect(beat) {
        if (running && exercise.mode == PracticeMode.Beat && beat >= 0) {
            if (preBeats > 0) preBeats-- else completedBeats++
            if (preBeats == 0 && completedBeats > 0 && completedBeats % (beatsPerRound * 2) == 0 && beat == beatsPerBar - 1 && autoIncrease && bpm < targetBpm) {
                bpm = (bpm + 5).coerceAtMost(targetBpm)
            }
            if (completedBeats >= totalBeats) { pause(); finished = true }
        }
    }
    LaunchedEffect(elapsed, checks, completedBeats, questionIndex, answer, correct, finished, rating, problems, bpm, rounds, meter, subdivision, autoIncrease, targetBpm) { snapshot() }
    fun listen() {
        playback?.cancel(); audioError = null
        playback = scope.launch {
            try {
                ChordPlayer.play(question.notes.mapIndexed { i, note -> PluckEvent(0, note, "听音", if (question.simultaneous) 0 else i * 900) }) {}
            } catch (c: kotlinx.coroutines.CancellationException) { throw c }
            catch (_: Exception) { audioError = "播放失败，请重试。" }
            finally { playback = null }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ pause(); showExit = true }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "退出本次练习") }
            Column { Text(exercise.title, fontWeight = FontWeight.Bold, fontSize = 22.sp); Text(formatDuration(elapsed), color = ToolPalette.muted) }
        } }
        item { Text(if (finished) "练习结果" else if (running) "保持放松，专注当前动作" else if (started) "练习已暂停" else "准备开始", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        if (!finished) {
            if (exercise.mode == PracticeMode.Beat) {
                item { PracticePanel {
                    Text(if (running && preBeats > 0) "预备拍 · 还剩 $preBeats 拍" else "当前动作", color = ToolPalette.accent)
                    val index = (completedBeats.coerceAtLeast(1) - 1) / if (exercise.chords.size > 1) beatsPerBar else 1
                    Text(exercise.actions[index % exercise.actions.size], fontSize = 48.sp, fontWeight = FontWeight.Bold, color = ToolPalette.ink)
                    Text("下一项 → ${exercise.actions[(index + 1) % exercise.actions.size]}", color = ToolPalette.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(beatsPerBar) { i ->
                        Surface(color = if (running && beat == i) ToolPalette.accent else ToolPalette.panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ToolPalette.line)) {
                            Text("${i + 1}", color = if (running && beat == i) MaterialTheme.colorScheme.onPrimary else ToolPalette.muted, modifier = Modifier.padding(17.dp))
                        }
                    } }
                    Text("${completedBeats / (totalBeats / rounds)} / $rounds 轮 · $bpm BPM", color = ToolPalette.muted)
                    LinearProgressIndicator(progress = { completedBeats.toFloat() / totalBeats }, modifier = Modifier.fillMaxWidth())
                } }
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton({ pause(); bpm = (bpm - 5).coerceAtLeast(40) }) { Text("− 5") }
                    Text("$bpm BPM", Modifier.weight(1f).padding(horizontal = 12.dp))
                    OutlinedButton({ pause(); bpm = (bpm + 5).coerceAtMost(200) }) { Text("+ 5") }
                } }
                if (!started) item { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目标轮数：$rounds", Modifier.weight(1f))
                    TextButton({ rounds = (rounds - 1).coerceAtLeast(1) }) { Text("−") }
                    TextButton({ rounds = (rounds + 1).coerceAtMost(32) }) { Text("+") }
                } }
                if (!started) item { PracticePanel {
                    Text("节拍配置", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3/4", "4/4", "6/8", "5/4", "7/8").forEach { value ->
                            FilterChip(meter == value, { meter = value }, { Text(value) })
                        }
                    }
                    Text("细分（每拍声音数量）", color = ToolPalette.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 4).forEach { value -> FilterChip(subdivision == value, { subdivision = value }, { Text("$value") }) }
                    }
                    Text("6/8、7/8 速度以八分音符为单位；动作按所选拍数重复，额外拍位会延续动作序列。", color = ToolPalette.muted, fontSize = 11.sp)
                } }
                item { PracticePanel {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("每 2 轮提速 5 BPM", Modifier.weight(1f)); Switch(autoIncrease, { autoIncrease = it }) }
                    if (autoIncrease) {
                        Text("目标上限：$targetBpm BPM")
                        Slider(targetBpm.toFloat(), { targetBpm = it.toInt().coerceIn(40, 200) }, valueRange = 40f..200f)
                        Text("提速是计划，不是自动判断你弹对了；跟不上时关闭并降速。", color = ToolPalette.muted, fontSize = 12.sp)
                    }
                } }
            }
            if (exercise.mode == PracticeMode.Guided) {
                items(exercise.steps.withIndex().toList()) { (i, step) ->
                    PracticePanel { Text(step.title, fontWeight = FontWeight.Bold); Text(step.text, color = ToolPalette.muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checks.split(',').contains(i.toString()), { value ->
                                started = true
                                val values = checks.split(',').filter { it.isNotBlank() }.toMutableSet()
                                if (value) values.add(i.toString()) else values.remove(i.toString()); checks = values.joinToString(",")
                            }); Text("我已完成并检查", fontSize = 13.sp)
                        }
                    }
                }
            }
            if (exercise.mode == PracticeMode.Ear) {
                item { PracticePanel {
                    Text("第 ${questionIndex + 1} / 10 题", color = ToolPalette.accent)
                    Text(if (exercise.earType == "pitch") "第二个音与第一个相比？" else "听到了哪一种？", fontSize = 22.sp)
                    Button({ started = true; running = true; heard = true; listen() }) { Text(if (playback != null) "重新播放" else "播放题目") }
                    EarTraining.options(exercise.earType).forEachIndexed { index, option ->
                        OutlinedButton({
                            if (answer < 0) { answer = index; if (index == question.answer) correct++; started = true }
                        }, Modifier.fillMaxWidth(), enabled = answer < 0 && heard) { Text(option) }
                    }
                    if (answer >= 0) {
                        Text(if (answer == question.answer) "回答正确" else "答案：${EarTraining.options(exercise.earType)[question.answer]}", color = ToolPalette.accent)
                        Text(question.explanation)
                        Button({
                            playback?.cancel(); playback = null
                            if (questionIndex == 9) { pause(); finished = true } else { questionIndex++; answer = -1; heard = false }
                        }) { Text(if (questionIndex == 9) "查看结果" else "下一题") }
                    }
                } }
            }
            item { if (exercise.mode != PracticeMode.Ear) Button({
                if (running) pause() else {
                    started = true; running = true
                    if (exercise.mode == PracticeMode.Beat) {
                        completedBeats -= completedBeats % beatsPerBar
                        preBeats = beatsPerBar
                        engine.update(MetronomeConfig(bpm = bpm, sound = sound, subdivision = subdivision).withMeter(meter))
                        engine.start()
                    }
                }
            }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (running) "暂停练习" else if (started) "继续本次练习" else if (exercise.mode == PracticeMode.Beat) "开始 · $beatsPerBar 拍预备" else "开始动作练习") } }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("练习时屏幕常亮", Modifier.weight(1f)); Switch(keepAwake, { keepAwake = it }) } }
            if (exercise.mode == PracticeMode.Beat) item { Row(verticalAlignment = Alignment.CenterVertically) { Text("节拍声音", Modifier.weight(1f)); Switch(sound, { sound = it }) } }
            item { Text(exercise.tip, color = ToolPalette.muted, lineHeight = 23.sp) }
            if (exercise.mode == PracticeMode.Guided) item { Button({ pause(); finished = true }, enabled = checks.split(',').count { it.isNotBlank() } == exercise.steps.size, modifier = Modifier.fillMaxWidth()) { Text("完成全部自检") } }
            item { TextButton({ pause(); showExit = true }) { Text("结束 / 保存为未完成") } }
            item { OutlinedButton({ pause(); elapsed = 0; completedBeats = 0; checks = ""; questionIndex = 0; correct = 0; answer = -1; started = false; seed = kotlin.random.Random.nextInt(); prefs.edit().remove("active.${exercise.id}").apply() }) { Text("重新开始") } }
        } else {
            item { PracticePanel {
                Text("${exercise.title} · ${formatDuration(elapsed)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (exercise.mode == PracticeMode.Ear) "答对 $correct / 10 题" else if (exercise.mode == PracticeMode.Beat) "$bpm BPM · 完成 $rounds 轮" else "${exercise.steps.size} 项动作自检已完成")
                Text("完成的是本次练习；动作质量由你的自检记录，不作自动演奏评分。", color = ToolPalette.muted)
            } }
            item { Text("这次感觉如何？", fontWeight = FontWeight.Bold) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("轻松", "刚好", "有点难").forEach { r -> FilterChip(rating == r, { rating = r }, { Text(r) }) } } }
            item { Text("遇到的问题（可选）", color = ToolPalette.muted) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("有杂音", "跟不上", "转换停顿", "动作不理解", "手部不适").forEach { problem ->
                FilterChip(problems.split('、').contains(problem), {
                    val values = problems.split('、').filter { it.isNotBlank() }.toMutableSet()
                    if (!values.add(problem)) values.remove(problem); problems = values.joinToString("、")
                }, { Text(problem) })
            } } }
            item { Button({ save(true) }, enabled = !saving && rating.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saving) "保存中…" else "保存练习结果") } }
        }
        item { (engineError ?: audioError ?: saveError)?.let { Text(it, color = ToolPalette.attention) } }
    }
    if (showExit) AlertDialog(onDismissRequest = { showExit = false }, title = { Text("保存本次练习？") },
        text = { Text(if (finished) "已达到练习目标。可以返回结果页填写自评后保存。" else "练习尚未完成。保存后会保留进度，下次进入可继续本次练习。") },
        confirmButton = { TextButton({ if (finished) showExit = false else { snapshot(); showExit = false; save(false) } }, enabled = !saving) { Text(if (finished) "返回结果" else "保存为未完成") } },
        dismissButton = { TextButton({ prefs.edit().remove("active.${exercise.id}").apply(); onExit() }) { Text("放弃本次") } })
}
