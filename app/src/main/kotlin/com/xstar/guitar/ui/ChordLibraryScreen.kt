package com.xstar.guitar.ui

import android.app.Activity
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xstar.guitar.audio.ChordPlayer
import com.xstar.guitar.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.min

private val ChordBg get() = ToolPalette.background
private val ChordPanel get() = ToolPalette.panel
private val ChordLine get() = ToolPalette.line
private val ChordInk get() = ToolPalette.ink
private val ChordMuted get() = ToolPalette.muted
private val ChordLime get() = ToolPalette.accent
private val ChordOrange get() = ToolPalette.attention

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordLibraryScreen(
    reduceMotion: Boolean = false,
    onTuner: () -> Unit = {},
    onMetronome: () -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chord_library", 0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var mode by rememberSaveable { mutableStateOf("popular") }
    var query by rememberSaveable { mutableStateOf("") }
    var root by rememberSaveable { mutableStateOf("all") }
    var quality by rememberSaveable { mutableStateOf("all") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var chordId by rememberSaveable { mutableStateOf<String?>(null) }
    var variant by rememberSaveable { mutableIntStateOf(0) }
    var finger by rememberSaveable { mutableIntStateOf(0) }
    var leftHand by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(PickingStyle.Down) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var activePluck by remember { mutableStateOf<PluckEvent?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var playback by remember { mutableStateOf<Job?>(null) }
    val chord = ChordLibrary.chords.find { it.id == chordId }
    val voicings = chord?.let { ChordLibrary.voicings(it) }.orEmpty()
    val voicing = voicings.getOrNull(variant) ?: voicings.firstOrNull()
    fun stop() { playback?.cancel(); playback = null; running = false; activePluck = null }
    fun play(events: List<PluckEvent>) {
        val previous = playback
        previous?.cancel()
        running = true; error = null; activePluck = null
        playback = scope.launch {
            try {
                previous?.cancelAndJoin()
                ChordPlayer.play(events) { activePluck = it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "试听暂时不可用，请检查设备声音输出后重试" }
            finally { if (playback === coroutineContext[Job]) { running = false; activePluck = null } }
        }
    }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("favorites", favorites).apply()
    }
    fun back() { stop(); if (chord != null) chordId = null else onClose() }
    BackHandler { if (sheet != null) sheet = null else back() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val activity = context as? Activity
        val previousStream = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); stop(); if (previousStream != null) activity.volumeControlStream = previousStream }
    }
    val results = remember(mode, query, root, quality, favorites) {
        ChordLibrary.search(query).filter {
            (mode == "all" || mode == "popular" && it.id in ChordLibrary.popular || mode == "favorites" && it.id in favorites) &&
                (root == "all" || it.root == root) && (quality == "all" || it.type.id == quality)
        }
    }
    val pageCount = maxOf(1, (results.size + 3) / 4)
    val visiblePage = page.coerceIn(0, pageCount - 1)
    Surface(Modifier.fillMaxSize(), color = ChordBg) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                val compact = maxHeight < 650.dp
                Column(Modifier.fillMaxSize().widthIn(max = 600.dp).align(Alignment.TopCenter).padding(horizontal = if (maxWidth < 360.dp) 16.dp else 22.dp)) {
                    Spacer(Modifier.height(12.dp))
                    if (chord == null) {
                        Text("和弦实验室。", fontSize = if (compact) 23.sp else 27.sp, fontWeight = FontWeight.SemiBold)
                        Text("找到指法，听见每一种声音", color = ChordMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                        OutlinedTextField(query, onValueChange = { query = it; page = 0; if (it.isNotBlank() && mode == "popular") mode = "all" },
                            modifier = Modifier.fillMaxWidth().height(52.dp), singleLine = true, shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("搜索 C、Am、Cmaj7", fontSize = 12.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            leadingIcon = { Icon(Icons.Rounded.Search, "搜索和弦", Modifier.size(19.dp)) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = ""; page = 0 }) { Icon(Icons.Rounded.Close, "清空搜索") } })
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(ChordLine))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                listOf("popular" to "常用和弦", "all" to "全部和弦", "favorites" to "我的收藏").forEach { (value, label) ->
                                    val active = mode == value
                                    Box(Modifier.width(IntrinsicSize.Max).height(48.dp).selectable(active, role = Role.Tab, onClick = { mode = value; page = 0 })) {
                                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(label, color = if (active) ChordLime else ChordMuted, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                                            if (value == "favorites" && favorites.isNotEmpty()) Text(favorites.size.toString(), color = if (active) ChordLime else ChordMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        if (active) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(ChordLime))
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChordFilterButton("根音", if (root == "all") "全部" else root, root != "all", Modifier.weight(1f)) { sheet = "root-filter" }
                            ChordFilterButton("类型", ChordLibrary.types.find { it.id == quality }?.shortName ?: "全部", quality != "all", Modifier.weight(1f)) { sheet = "quality-filter" }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (query.isNotBlank()) "搜索结果" else if (mode == "favorites") "留住喜欢的声音" else "探索和弦", fontSize = 11.sp)
                            Text("${results.size} CHORDS", color = ChordMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (results.isEmpty()) {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (mode == "favorites" && favorites.isEmpty()) "点卡片上的 ☆，留住喜欢的和弦" else "没有匹配的和弦，试试其他筛选", fontSize = 12.sp, color = ChordMuted)
                                    TextButton({ mode = "all"; query = ""; root = "all"; quality = "all"; page = 0 }) { Text("查看全部和弦 →") }
                                }
                            } else {
                                val visible = results.drop(visiblePage * 4).take(4)
                                repeat(2) { row ->
                                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        repeat(2) { col ->
                                            val item = visible.getOrNull(row * 2 + col)
                                            if (item == null) Spacer(Modifier.weight(1f)) else ChordCard(item, item.id in favorites, Modifier.weight(1f).fillMaxHeight(),
                                                onOpen = { chordId = item.id; variant = 0; finger = 0; error = null }, onFavorite = { toggleFavorite(item.id) })
                                        }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton({ page = visiblePage - 1 }, enabled = visiblePage > 0, shape = RoundedCornerShape(12.dp)) { Text("← 上一页", fontSize = 11.sp) }
                            Text("${visiblePage + 1} / $pageCount", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChordMuted)
                            OutlinedButton({ page = visiblePage + 1 }, enabled = visiblePage < pageCount - 1, shape = RoundedCornerShape(12.dp)) { Text("下一页 →", fontSize = 11.sp) }
                        }
                    } else if (voicing != null) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(chord.id, fontSize = if (compact) 34.sp else 42.sp, fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 38.sp else 46.sp)
                                Text("${chord.type.name} · ${if (voicing.easy) "新手友好" else "进阶指法"}", color = ChordMuted, fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(chord.tones.joinToString(" · "), color = ChordLime, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                ChordRoundButton(if (chord.id in favorites) "取消收藏 ${chord.id}" else "收藏 ${chord.id}", { toggleFavorite(chord.id) }) {
                                    Icon(if (chord.id in favorites) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = if (chord.id in favorites) ChordLime else ChordMuted)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("找到舒服的指法", fontSize = 12.sp)
                            Text("${variant + 1} / ${voicings.size} VOICINGS", color = ChordMuted, fontSize = 10.sp)
                        }
                        ChordChipRow(voicings.mapIndexed { index, v -> index.toString() to v.name }, variant.toString()) { stop(); variant = it.toInt(); finger = 0; error = null }
                        Spacer(Modifier.height(7.dp))
                        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFF181D18), border = BorderStroke(1.dp, ChordLine)) {
                            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${voicing.shape} 型 · 标准调弦", fontSize = 10.sp, color = ChordMuted)
                                    TextButton({ leftHand = !leftHand }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (leftHand) "✓ 左手琴 ⇄" else "左手琴 ⇄", fontSize = 10.sp, color = if (leftHand) ChordLime else ChordMuted) }
                                }
                                ChordDiagram(voicing, Modifier.weight(1f).fillMaxWidth(), leftHand, finger,
                                    if (reduceMotion) -1 else activePluck?.stringIndex ?: -1, true,
                                    onFinger = { finger = it; if (compact) sheet = "finger" }, onString = { string ->
                                        if (voicing.frets[string] >= 0) play(listOf(PluckEvent(string, ChordLibrary.tuning[string] + voicing.frets[string], "单弦", 0)))
                                        else error = "${6 - string} 弦不参与这个指法"
                                    })
                                Text("○ 空弦   × 不弹   1–4 手指编号", Modifier.fillMaxWidth(), color = ChordMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
                                if (!compact) Text("点编号看按法 · 点底部弦号听单音", Modifier.fillMaxWidth().padding(top = 3.dp), color = ChordMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }
                        if (!compact) Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (1..4).forEach { f -> ChordChip("$f ${ChordLibrary.fingerNames[f]}", finger == f, Modifier.weight(1f)) { finger = f } }
                        }
                        if (!compact) Surface(Modifier.fillMaxWidth(), color = ChordLime.copy(alpha = .04f), shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(if (finger == 0) "先放稳手指，再轻轻扫弦" else "$finger · ${ChordLibrary.fingerNames[finger]}", color = ChordLime, fontSize = 11.sp)
                                Text(if (finger == 0) "点手指编号，查看落指位置与横按方式。" else voicing.fingerInstruction(finger), fontSize = 11.sp, color = ChordMuted, lineHeight = 16.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("steps" to "怎么按", "theory" to "和弦组成", "tips" to "小技巧").forEach { (key, name) -> TextButton({ sheet = key }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↗ $name", fontSize = 11.sp) } }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PickingStyle.entries.forEach { style -> ChordChip(style.label, picking == style, Modifier.weight(1f)) { stop(); picking = style } }
                        }
                        Text(error ?: activePluck?.let { "${it.finger} · ${6 - it.stringIndex} 弦" } ?: picking.hint,
                            Modifier.fillMaxWidth().padding(vertical = 4.dp), color = if (error != null) ChordOrange else if (activePluck != null) ChordLime else ChordMuted,
                            fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                        Button({ if (running) stop() else play(ChordLibrary.sequence(voicing, picking)) }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp)); Text(if (running) "停止试听" else "听听这个和弦", fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = ChordPanel) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (sheet) {
                    "root-filter", "quality-filter" -> {
                        val choosingRoot = sheet == "root-filter"
                        Text(if (choosingRoot) "选择根音" else "选择和弦类型", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (choosingRoot) "从一个音出发，探索它的和弦" else "选择你想听见的声音色彩", color = ChordMuted, fontSize = 12.sp)
                        val options = if (choosingRoot) listOf("all" to "全部根音") + ChordLibrary.roots.map { it to it }
                            else listOf("all" to "全部类型") + ChordLibrary.types.map { it.id to it.name }
                        val selectedValue = if (choosingRoot) root else quality
                        options.chunked(if (choosingRoot) 3 else 2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                row.forEach { (id, label) -> ChordChip(label, id == selectedValue, Modifier.weight(1f)) {
                                    if (choosingRoot) root = id else quality = id
                                    page = 0; sheet = null
                                } }
                                repeat((if (choosingRoot) 3 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    "menu" -> {
                        Text("你的口袋练习室", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        TextButton({ sheet = null; stop(); onTuner() }) { Text("调音器 · 先让每根弦准备好 ↗") }
                        TextButton({ sheet = null; stop(); onMetronome() }) { Text("节拍器 · 找到你的节奏 ↗") }
                        TextButton({ sheet = "help" }) { Text("读懂和弦图 · 新手指南 ↗") }
                        TextButton({ sheet = null; stop(); onClose() }) { Text("返回工具页") }
                    }
                    "help" -> {
                        Text("一分钟读懂和弦图", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("默认左侧是最粗的 6 弦，右侧是最细的 1 弦。横线是品丝，左侧数字是实际品位；左手琴视图镜像左右顺序。")
                        Text("1 食指 · 2 中指 · 3 无名指 · 4 小指。连接相同编号的横条表示一根手指横按多根弦。")
                        Text("○ 表示弹空弦，× 表示不弹。点底部弦号试听单音。右手分解试听为 P 拇指、i 食指、m 中指、a 无名指；少弦指法自动减少手指。")
                        Text("标准调弦 · 120 个和弦 · 253 套指法。试听使用合成拨弦音，音名采用常见等音写法。", color = ChordMuted, fontSize = 12.sp)
                    }
                    "finger" -> if (voicing != null) {
                        Text("$finger · ${ChordLibrary.fingerNames[finger]}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(voicing.fingerInstruction(finger))
                    }
                    "steps" -> if (voicing != null) {
                        Text("${chord?.id} · 怎么按", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        val muted = voicing.frets.indices.filter { voicing.frets[it] < 0 }.map { 6 - it }
                        val open = voicing.frets.indices.filter { voicing.frets[it] == 0 }.map { 6 - it }
                        Text(if (muted.isEmpty()) "六根弦都可以弹。" else "${muted.joinToString("、")} 弦不弹。")
                        Text(if (open.isEmpty()) "没有空弦，每根发声弦都需要按住。" else "${open.joinToString("、")} 弦保持空弦。")
                        voicing.fingers.filter { it > 0 }.distinct().sorted().forEach { f -> Text("$f ${ChordLibrary.fingerNames[f]}：${voicing.fingerInstruction(f)}") }
                        Text("从 ${6 - voicing.soundingStrings.first()} 弦向 1 弦扫动，逐根检查声音是否清晰。")
                    }
                    "theory" -> if (chord != null) {
                        Text("${chord.id} · 和弦组成", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        chord.tones.forEachIndexed { i, tone -> Text("$tone · ${chord.type.roles[i]}", color = ChordLime) }
                        Text("不同指法改变音的高低与排列，形成不同听感；部分七和弦指法会省略纯五度。音名采用常见等音写法。", color = ChordMuted)
                    }
                    "tips" -> {
                        Text("让每根弦都清晰", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (voicing?.easy == true) "让手指像小拱桥一样立起，避免碰到相邻空弦。" else "横按时把食指稍向拇指方向侧转，用较硬的指侧贴住琴弦。拇指放在琴颈后方，避免只靠捏紧用力。")
                        Text("有闷音？逐根弹响，找到没按实的那根弦。手指靠近品丝后方，用最小的力量获得清晰声音。")
                    }
                }
            }
        }
}

@Composable
private fun ChordFilterButton(label: String, value: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier.height(52.dp), shape = RoundedCornerShape(14.dp), color = if (active) ChordLime.copy(alpha = .05f) else ChordPanel,
        border = BorderStroke(1.dp, if (active) ChordLime.copy(alpha = .45f) else ChordLine)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, color = ChordMuted)
            Spacer(Modifier.width(10.dp))
            Text(value, Modifier.weight(1f), fontSize = 13.sp, color = if (active) ChordLime else ChordInk, fontWeight = FontWeight.Medium)
            Text("⌄", color = if (active) ChordLime else ChordMuted, fontSize = 17.sp)
        }
    }
}

@Composable
private fun ChordRoundButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick, Modifier.size(48.dp).semantics { contentDescription = label }, shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, ChordLine)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ChordChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items.forEach { (id, label) -> ChordChip(label, id == selected) { onSelect(id) } }
    }
}

@Composable
private fun ChordChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier.heightIn(min = 42.dp).semantics { selected = active }, shape = RoundedCornerShape(11.dp), color = if (active) ChordLime.copy(alpha = .1f) else ChordPanel,
        border = BorderStroke(1.dp, if (active) ChordLime.copy(alpha = .6f) else ChordLine)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.Center) { Text(label, fontSize = 11.sp, color = if (active) ChordLime else ChordMuted, maxLines = 1) }
    }
}

@Composable
private fun ChordCard(chord: GuitarChord, favorite: Boolean, modifier: Modifier, onOpen: () -> Unit, onFavorite: () -> Unit) {
    val variants = ChordLibrary.voicings(chord)
    Surface(modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClickLabel = "查看 ${chord.id} 指法", onClick = onOpen), shape = RoundedCornerShape(20.dp), color = ChordPanel, border = BorderStroke(1.dp, ChordLine)) {
        Box {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(chord.id, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(chord.type.name, color = ChordMuted, fontSize = 10.sp)
                ChordDiagram(variants.first(), Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (variants.first().easy) "入门" else "进阶", color = if (variants.first().easy) ChordLime else ChordOrange, fontSize = 10.sp)
                    Text("${variants.size} 种指法 ↗", color = ChordMuted, fontSize = 10.sp)
                }
            }
            IconButton(onFavorite, Modifier.align(Alignment.TopEnd).size(44.dp)) { Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (favorite) "取消收藏 ${chord.id}" else "收藏 ${chord.id}", tint = if (favorite) ChordLime else ChordMuted, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Canvas lines plus real clickable, accessible Compose controls for fingers and strings. */
@Composable
private fun ChordDiagram(
    voicing: ChordVoicing, modifier: Modifier, mirror: Boolean = false, finger: Int = 0, activeString: Int = -1,
    interactive: Boolean = false, onFinger: (Int) -> Unit = {}, onString: (Int) -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val diagramHeight = maxHeight
        val width = min(maxWidth.value * .70f, diagramHeight.value * .8f).coerceAtLeast(0f).dp
        val step = width / 5
        val top = diagramHeight * .13f
        val fretHeight = diagramHeight * .72f / 5
        fun x(index: Int) = step * if (mirror) 5 - index else index
        fun y(fret: Int) = top + fretHeight * (fret - voicing.startFret + .5f)
        Box(Modifier.width(width).fillMaxHeight().align(Alignment.Center)) {
            Canvas(Modifier.fillMaxSize()) {
                val topPx = top.toPx()
                val fretPx = fretHeight.toPx()
                repeat(6) { fret -> drawLine(if (fret == 0 && voicing.startFret == 1) ChordMuted else ChordLine, Offset(0f, topPx + fret * fretPx), Offset(size.width, topPx + fret * fretPx), if (fret == 0 && voicing.startFret == 1) 3.dp.toPx() else 1.dp.toPx()) }
                repeat(6) { string -> drawLine(if (activeString == string) ChordLime else ChordMuted.copy(alpha = .65f), Offset(x(string).toPx(), topPx), Offset(x(string).toPx(), topPx + fretPx * 5), (if (activeString == string) 2.4f else 1.4f - string * .14f).dp.toPx()) }
                voicing.frets.indices.filter { voicing.frets[it] > 0 }.groupBy { voicing.fingers[it] to voicing.frets[it] }.forEach { (key, strings) ->
                    if (strings.size > 1) drawLine(if (finger == 0 || finger == key.first) ChordLime else Color(0xFF566344), Offset(x(strings.first()).toPx(), y(key.second).toPx()), Offset(x(strings.last()).toPx(), y(key.second).toPx()), min(19.dp.toPx(), fretPx * .65f), StrokeCap.Round)
                }
            }
            if (interactive) repeat(5) { fret ->
                Text((voicing.startFret + fret).toString(), Modifier.offset(x = (-27).dp, y = top + fretHeight * (fret + .5f) - 7.dp), color = ChordMuted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            repeat(6) { string ->
                val fret = voicing.frets[string]
                if (fret <= 0) Text(if (fret < 0) "×" else "○", Modifier.offset(x = x(string) - 10.dp, y = top - 23.dp).width(20.dp), color = if (fret < 0) ChordMuted else ChordLime, fontSize = 15.sp, textAlign = TextAlign.Center)
                else {
                    val f = voicing.fingers[string]
                    val diameter = min(28f, fretHeight.value * .9f).coerceAtLeast(10f).dp
                    Box(Modifier.offset(x = x(string) - if (interactive) 20.dp else diameter / 2, y = y(fret) - if (interactive) 20.dp else diameter / 2)
                        .size(if (interactive) 40.dp else diameter).then(if (interactive) Modifier.clickable(role = Role.Button, onClick = { onFinger(f) }).semantics { contentDescription = "${ChordLibrary.fingerNames[f]}，${6-string} 弦 $fret 品" } else Modifier), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(diameter).background(if (finger == 0 || finger == f) ChordLime else Color(0xFF566344), CircleShape), contentAlignment = Alignment.Center) {
                            Text(f.toString(), color = ChordBg, fontWeight = FontWeight.SemiBold, fontSize = if (diameter < 20.dp) 9.sp else 12.sp, lineHeight = 13.sp)
                        }
                    }
                }
                if (interactive) Box(Modifier.offset(x = x(string) - 20.dp, y = diagramHeight - 36.dp).size(40.dp, 36.dp).clickable(role = Role.Button, onClick = { onString(string) })
                    .semantics { contentDescription = "${6-string} 弦${if (fret < 0) "不弹" else "，点击试听"}" }, contentAlignment = Alignment.Center) {
                    Text((6 - string).toString(), color = if (activeString == string) ChordLime else ChordMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
