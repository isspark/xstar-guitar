package com.xstar.guitar

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xstar.guitar.ui.LocalXStarColors
import com.xstar.guitar.ui.ThemePreset
import com.xstar.guitar.ui.ThemeSettings
import com.xstar.guitar.ui.ScoreReaderScreen
import com.xstar.guitar.ui.ImageCollectionScreen
import com.xstar.guitar.audio.AudioTuner
import com.xstar.guitar.data.LessonProgressEntity
import com.xstar.guitar.data.XStarDatabase
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class Destination(val title: String, val subtitle: String, val icon: ImageVector) {
    Home("今天", "下一步与继续学习", Icons.Rounded.Home),
    Learn("学习", "课程与新手路径", Icons.Rounded.School),
    Practice("练习", "和弦、转换与节奏", Icons.Rounded.FitnessCenter),
    Sheets("曲谱", "乐库、收藏与导入", Icons.Rounded.LibraryMusic),
    Tools("工具", "调音器、节拍器与和弦", Icons.Rounded.Build),
}

private enum class Overlay { None, Tuner, Settings, Metronome, Chords, Practice, Song, ImageCollection }

@Composable
fun XStarApp(
    themeSettings: ThemeSettings,
    appPreferences: AppPreferences,
    database: XStarDatabase,
    onThemeSettingsChanged: (ThemeSettings) -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.Home) }
    var drawerOpen by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var selectedSongId by remember { mutableStateOf("zui_lang_man_de_shi") }
    var selectedSongTitle by remember { mutableStateOf("最浪漫的事") }
    var selectedCollection by remember { mutableStateOf<Pair<String, List<String>>>("" to emptyList()) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingImportName by remember { mutableStateOf("") }
    var namingImport by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var onboarding by remember { mutableStateOf(!appPreferences.onboardingDone) }
    var edgeDrag by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val edgeWidth = with(density) { 32.dp.toPx() }
    val threshold = with(density) { 60.dp.toPx() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            appPreferences.addImportedFile(it.lastPathSegment?.substringAfterLast('/') ?: "已导入曲谱", it.toString())
        }
    }
    val imageCollectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris
            pendingImportName = if (uris.size == 1) {
                uris.first().lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { "图片曲谱" } ?: "图片曲谱"
            } else "图片曲谱 ${System.currentTimeMillis().toString().takeLast(4)}"
            namingImport = true
        }
    }
    val lessons by database.progressDao().observeLessons().collectAsState(initial = emptyList())
    val practiceHistory by database.progressDao().observePractice().collectAsState(initial = emptyList())

    BackHandler(drawerOpen || overlay != Overlay.None) {
        if (overlay != Overlay.None) overlay = Overlay.None else drawerOpen = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(drawerOpen, overlay) {
                var startedAtEdge = false
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (overlay == Overlay.Song) {
                            startedAtEdge = false
                            return@detectHorizontalDragGestures
                        }
                        startedAtEdge = offset.x >= size.width - edgeWidth
                        edgeDrag = 0f
                    },
                    onDragEnd = {
                        if (!drawerOpen && overlay == Overlay.None && startedAtEdge && edgeDrag < -threshold) drawerOpen = true
                        if (drawerOpen && edgeDrag > threshold) drawerOpen = false
                        edgeDrag = 0f
                    },
                ) { _, amount -> edgeDrag += amount }
            },
    ) {
        GlowBackground(themeSettings)
        Scaffold(containerColor = Color.Transparent) { scaffoldPadding ->
            when (destination) {
                Destination.Home -> HomeScreen(scaffoldPadding.calculateTopPadding(), themeSettings.compact, { drawerOpen = true }, { overlay = Overlay.Metronome }, { overlay = Overlay.Chords }) { overlay = Overlay.Tuner; appPreferences.tunerOpened = true }
                Destination.Learn -> LearnScreen(scaffoldPadding.calculateTopPadding(), themeSettings.compact, lessons.any { it.status == "COMPLETED" }, { drawerOpen = true }) { overlay = Overlay.Tuner; appPreferences.tunerOpened = true }
                Destination.Practice -> com.xstar.guitar.ui.PracticeLibraryScreen(
                    database = database,
                    onMenu = { drawerOpen = true },
                    onChords = { overlay = Overlay.Chords },
                    onTuner = { overlay = Overlay.Tuner; appPreferences.tunerOpened = true },
                    onSong = { id, title -> selectedSongId = id; selectedSongTitle = title; overlay = Overlay.Song },
                )
                Destination.Sheets -> SheetsScreen(scaffoldPadding.calculateTopPadding(), themeSettings.compact, appPreferences, libraryRevision, { imageCollectionLauncher.launch("image/*") }, { renameTarget = it; renameValue = it }, { deleteTarget = it }, { libraryRevision++ }, { name, uris -> selectedCollection = name to uris; overlay = Overlay.ImageCollection }, { id, title -> selectedSongId = id; selectedSongTitle = title; overlay = Overlay.Song }) { drawerOpen = true }
                Destination.Tools -> ToolsScreen(scaffoldPadding.calculateTopPadding(), themeSettings.compact, { drawerOpen = true }, { overlay = Overlay.Tuner; appPreferences.tunerOpened = true }, { overlay = Overlay.Metronome }, { overlay = Overlay.Chords })
            }
        }

        RightDrawer(
            visible = drawerOpen,
            current = destination,
            reduceMotion = themeSettings.reduceMotion,
            onDismiss = { drawerOpen = false },
            onDestination = {
                destination = it
                drawerOpen = false
            },
            onSettings = {
                drawerOpen = false
                overlay = Overlay.Settings
            },
        )

        if (overlay == Overlay.Tuner) com.xstar.guitar.ui.TunerScreen(database) { overlay = Overlay.None }
        if (overlay == Overlay.Metronome) com.xstar.guitar.ui.MetronomeScreen(themeSettings.reduceMotion) { overlay = Overlay.None }
        if (overlay == Overlay.Chords) com.xstar.guitar.ui.ChordLibraryScreen(
            reduceMotion = themeSettings.reduceMotion,
            onTuner = { overlay = Overlay.Tuner; appPreferences.tunerOpened = true },
            onMetronome = { overlay = Overlay.Metronome },
        ) { overlay = Overlay.None }
        if (overlay == Overlay.Practice) com.xstar.guitar.ui.PracticeLibraryScreen(
            database = database, onMenu = {}, startWithExercise = true,
            onChords = { overlay = Overlay.Chords },
            onTuner = { overlay = Overlay.Tuner; appPreferences.tunerOpened = true },
            onSong = { id, title -> selectedSongId = id; selectedSongTitle = title; overlay = Overlay.Song },
            onClose = { overlay = Overlay.None },
        )
        if (overlay == Overlay.Song) ScoreReaderScreen(selectedSongId, selectedSongTitle) { overlay = Overlay.None }
        if (overlay == Overlay.ImageCollection) ImageCollectionScreen(selectedCollection.first, selectedCollection.second) { overlay = Overlay.None }
        if (overlay == Overlay.Settings) AppearanceScreen(themeSettings, onThemeSettingsChanged) { overlay = Overlay.None }
        if (namingImport) {
            AlertDialog(
                onDismissRequest = { namingImport = false },
                title = { Text("命名曲谱") },
                text = {
                    OutlinedTextField(
                        value = pendingImportName,
                        onValueChange = { pendingImportName = it },
                        singleLine = true,
                        label = { Text("曲谱名称") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = pendingImportName.trim().ifBlank { "图片曲谱" }
                        pendingImportUris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                        appPreferences.addImportedCollection(name, pendingImportUris.map(Uri::toString))
                        libraryRevision++
                        namingImport = false
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { namingImport = false }) { Text("取消") } },
            )
        }
        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("重命名曲谱") },
                text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("曲谱名称") }) },
                confirmButton = {
                    TextButton(onClick = {
                        val value = renameValue.trim()
                        if (value.isNotBlank()) { appPreferences.renameImportedCollection(target, value); libraryRevision++ }
                        renameTarget = null
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
            )
        }
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("删除曲谱？") },
                text = { Text("确定删除“$target”吗？删除后无法恢复。") },
                confirmButton = {
                    TextButton(onClick = { appPreferences.removeImportedCollection(target); deleteTarget = null; libraryRevision++ }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
            )
        }
        if (onboarding) WelcomeScreen(
            onStart = { appPreferences.onboardingDone = true; onboarding = false },
            onTools = { appPreferences.onboardingDone = true; onboarding = false; destination = Destination.Tools },
        )
    }
}

@Composable
private fun GlowBackground(settings: ThemeSettings) {
    if (!settings.glow) return
    val colors = LocalXStarColors.current
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(brush = Brush.radialGradient(listOf(colors.glow, Color.Transparent)), radius = size.minDimension * .65f, center = Offset(size.width * .12f, 0f))
        drawCircle(brush = Brush.radialGradient(listOf(colors.steady.copy(alpha = .10f), Color.Transparent)), radius = size.minDimension * .7f, center = Offset(size.width, size.height * .7f))
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, onMenu: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        }
        if (trailing != null) trailing()
        MenuButton(onMenu)
    }
}

@Composable
private fun MenuButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Menu, "打开导航菜单", tint = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun ScreenColumn(topPadding: androidx.compose.ui.unit.Dp, compact: Boolean, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = if (compact) 16.dp else 20.dp,
            end = if (compact) 16.dp else 20.dp,
            top = topPadding + 18.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
        content = content,
    )
}

@Composable
private fun HomeScreen(top: androidx.compose.ui.unit.Dp, compact: Boolean, onMenu: () -> Unit, onMetronome: () -> Unit, onChords: () -> Unit, onTuner: () -> Unit) {
    ScreenColumn(top, compact) {
        item { PageHeader("9 月 7 日 · 星期一", "今天，从听准开始", onMenu) }
        item { PrimaryActionCard(onTuner) }
        item { SectionTitle("常用工具", "随时可用") }
        item {
            ResponsiveRow {
                ToolShortcut(Icons.Rounded.GraphicEq, "调音器", "标准调弦 E A D G B E", onTuner)
                ToolShortcut(Icons.Rounded.Timer, "节拍器", "40–240 BPM · 跟随你的节奏", onMetronome)
                ToolShortcut(Icons.Rounded.MusicNote, "和弦库", "120 个和弦 · 指法与拨弦试听", onChords)
            }
        }
        item { SectionTitle("今天练 5 分钟", "轻量") }
        item { ListCard("6", "认识六根弦", "从最粗的第 6 弦开始") }
        item { SectionTitle("第一首歌", "准备中") }
        item { SongCard("01", "沿着晚风", "原创练习曲 · 入门", "Em · G · C · D · 72 BPM") }
    }
}

@Composable
private fun PrimaryActionCard(onClick: () -> Unit) {
    val colors = LocalXStarColors.current
    val primary = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = colors.raised),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(16.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = .32f), Color.Transparent)), radius = size.minDimension * .75f, center = Offset(size.width, 0f))
            }
            Column(Modifier.padding(26.dp)) {
                Tag("新手路径 · 第 1 步")
                Spacer(Modifier.height(38.dp))
                Text("先让六根弦回到正确声音", style = MaterialTheme.typography.headlineMedium)
                Text("完成标准调音，后面的和弦才会听起来正确。麦克风只在本机实时分析。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                Text("约 6 分钟  ·  需要一把吉他", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 18.dp))
                Button(onClick, shape = RoundedCornerShape(16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp)) { Text("开始第一次调音") }
            }
        }
    }
}

@Composable
private fun LearnScreen(top: androidx.compose.ui.unit.Dp, compact: Boolean, completed: Boolean, onMenu: () -> Unit, onTuner: () -> Unit) {
    val lessons = listOf(
        Triple("已完成", "认识你的吉他", "3 分钟 · 琴弦与品格"),
        Triple("现在学", "第一次标准调音", "6 分钟 · 使用调音器"),
        Triple("接下来", "弹响 Em 和弦", "8 分钟 · 两根手指"),
        Triple("", "保持四拍稳定", "7 分钟 · 节拍器"),
        Triple("", "Em 与 G 的第一次转换", "10 分钟 · 和弦转换"),
    )
    ScreenColumn(top, compact) {
        item { PageHeader("新手路径", "弹响第一首歌", onMenu) }
        item {
                 FeatureCard {
                Tag("当前里程碑")
                Text("把吉他调准", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                Text("先听懂六根弦，再用调音器让每根弦回到标准音高。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                 Text(if (completed) "已完成 · 可以开始和弦练习" else "2 个小步骤 · 约 10 分钟", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.LinearProgressIndicator(progress = { .18f }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(7.dp).clip(CircleShape))
            }
        }
        items(lessons) { lesson ->
            val active = lesson.second == "第一次标准调音"
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = active, onClick = onTuner),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (lesson.first.isNotEmpty()) Tag(lesson.first, lesson.first == "已完成")
                    Text(lesson.second, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = if (lesson.first.isEmpty()) 0.dp else 10.dp))
                    Text(lesson.third, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PracticeScreen(top: androidx.compose.ui.unit.Dp, compact: Boolean, onStart: () -> Unit, onMenu: () -> Unit) {
    ScreenColumn(top, compact) {
        item { PageHeader("自由练习", "今天想练什么？", onMenu) }
        item {
            FeatureCard(accent = true) {
                Tag("为你建议", true)
                Text("认识六根弦", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp))
                Text("这是第一次调音前最短的准备。完成后会回到当前学习路径。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("5 分钟 · 无需节拍器", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 14.dp))
                Button(onClick = onStart) { Text("开始练习") }
            }
        }
        item { SectionTitle("专项练习") }
        item {
            ResponsiveRow {
                PracticeType("Am", "和弦按法", "逐弦检查声音")
                PracticeType("A → E", "和弦转换", "双和弦与循环")
                PracticeType("↓  ↓", "节奏跟拍", "从 50 BPM 开始")
                PracticeType("▥", "曲目分段", "在歌曲中应用")
            }
        }
    }
}

@Composable
private fun SheetsScreen(top: androidx.compose.ui.unit.Dp, compact: Boolean, preferences: AppPreferences, libraryRevision: Int, onImport: () -> Unit, onRename: (String) -> Unit, onDelete: (String) -> Unit, onLibraryChanged: () -> Unit, onCollection: (String, List<String>) -> Unit, onSong: (String, String) -> Unit, onMenu: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val builtIn = listOf(
        Triple("最浪漫的事", "alphaTab SVG · C / Am / Dm / G", "zui_lang_man_de_shi"),
        Triple("遇见", "图片转录 · G / Em7 / Cadd9 / D", "yu_jian"),
        Triple("画", "图片转录 · 变调夹 4 品", "hua"),
        Triple("河岸边", "入门练习 · Am / C / G / D", ""),
    ).filter { query.isBlank() || it.first.contains(query.trim(), ignoreCase = true) }
    val imported = preferences.importedCollections.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }
    ScreenColumn(top, compact) {
        item { libraryRevision; Spacer(Modifier.height(0.dp)) }
        item {
            PageHeader("SCORE LIBRARY", "曲谱", onMenu) {
                IconButton(onClick = onImport) { Icon(Icons.Rounded.UploadFile, "导入图片曲谱") }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除搜索") } },
                placeholder = { Text("搜索曲谱名称") },
                shape = RoundedCornerShape(18.dp),
            )
        }
        item { SectionTitle("内置曲谱", "${builtIn.size} 首") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                gridItems(builtIn) { (title, subtitle, id) ->
                    ScoreLibraryCard(title, subtitle, "内置", onClick = { if (id.isNotBlank()) onSong(id, title) })
                }
            }
        }
        item { SectionTitle("我的曲谱", "${imported.size} 份") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                gridItems(imported) { name ->
                    ScoreLibraryCard(name, "图片曲谱 · ${preferences.urisForCollection(name).size} 页", "我的", onClick = { onCollection(name, preferences.urisForCollection(name)) }, onRename = { onRename(name) }, onDelete = { onDelete(name) })
                }
            }
        }
        if (builtIn.isEmpty() && imported.isEmpty()) item { ListCard("搜索", "没有找到曲谱", "试试其他曲谱名称") }
    }
}

@Composable
private fun ScoreLibraryCard(title: String, subtitle: String, badge: String, onClick: () -> Unit, onRename: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().height(166.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, LocalXStarColors.current.steady))), contentAlignment = Alignment.Center) {
                Icon(if (badge == "内置") Icons.Rounded.LibraryMusic else Icons.Rounded.Collections, null, tint = Color.White)
                }
                if (onRename != null) IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Edit, "重命名 $title", modifier = Modifier.size(19.dp)) }
                if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.DeleteOutline, "删除 $title", modifier = Modifier.size(19.dp)) }
            }
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 12.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            Text(badge.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ToolsScreen(top: androidx.compose.ui.unit.Dp, compact: Boolean, onMenu: () -> Unit, onTuner: () -> Unit, onMetronome: () -> Unit, onChords: () -> Unit) {
    ScreenColumn(top, compact) {
            item { PageHeader("无需进入课程", "工具", onMenu) }
            item { LargeTool(Icons.Rounded.GraphicEq, "调音器", "逐根听准标准六弦", onTuner, true) }
            item { LargeTool(Icons.Rounded.Timer, "节拍器", "40–240 BPM · 拍号、细分与重音", onMetronome, false) }
            item { LargeTool(Icons.Rounded.MusicNote, "和弦库", "查看按法、逐弦检查，再进入转换练习", onChords, false) }
    }
}

@Composable
private fun RightDrawer(
    visible: Boolean,
    current: Destination,
    reduceMotion: Boolean,
    onDismiss: () -> Unit,
    onDestination: (Destination) -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .48f)).clickable(onClick = onDismiss))
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = if (reduceMotion) fadeIn() else slideInHorizontally { it },
        exit = if (reduceMotion) fadeOut() else slideOutHorizontally { it },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onDismiss))
            Surface(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(.84f).widthIn(max = 400.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 24.dp,
            ) {
                Column(
                    Modifier.padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp,
                        start = 18.dp,
                        end = 18.dp,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp, 12.dp, 16.dp, 12.dp)).background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
                            Text("XS", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("XStar Guitar", fontWeight = FontWeight.Bold)
                            Text("下一步，清楚一点", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onDismiss) { Icon(Icons.Rounded.Close, "关闭导航菜单") }
                    }
                    Spacer(Modifier.height(22.dp))
                    Destination.entries.forEach { destination ->
                        DestinationItem(destination, destination == current) { onDestination(destination) }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSettings).padding(vertical = 18.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("外观、设置与本地数据", Modifier.weight(1f).padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .35f) else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(destination.icon, null, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(destination.title, fontWeight = FontWeight.SemiBold)
            Text(destination.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (selected) Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceScreen(settings: ThemeSettings, onChanged: (ThemeSettings) -> Unit, onClose: () -> Unit) {
    var customAccent by remember(settings.accent) { mutableLongStateOf(settings.accent) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        GlowBackground(settings)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("个性化", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Text("外观设置", style = MaterialTheme.typography.headlineLarge) }
                    IconButton(onClose) { Icon(Icons.Rounded.Close, "关闭外观设置") }
                }
            }
            item { ThemePreview() }
            item { SectionTitle("主题预设", "即时生效") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePreset.entries.forEach { preset -> ThemeOption(preset, preset == settings.preset, Modifier.weight(1f)) { onChanged(settings.copy(preset = preset, accent = preset.accent)) } }
                }
            }
            item { SectionTitle("自定义", "保存在本机") }
            item {
                FeatureCard {
                    Text("强调色", style = MaterialTheme.typography.titleMedium)
                    Text("用于按钮、进度和当前项目", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(0xFF886CFF, 0xFF417CFF, 0xFF00AFA0, 0xFFE15D96, 0xFFFF7043, 0xFFB88A00).forEach { color ->
                            AccentOption(color, customAccent == color) {
                                customAccent = color
                                onChanged(settings.copy(accent = color))
                            }
                        }
                    }
                }
            }
            item { SettingSwitch("背景光晕", "关闭后使用纯色背景", settings.glow) { onChanged(settings.copy(glow = it)) } }
            item { SettingSwitch("紧凑布局", "缩小区块间距与卡片留白", settings.compact) { onChanged(settings.copy(compact = it)) } }
            item { SettingSwitch("减少动画", "保留状态变化，移除位移动效", settings.reduceMotion) { onChanged(settings.copy(reduceMotion = it)) } }
        }
    }
}

@Composable
private fun ThemePreview() {
    val colors = LocalXStarColors.current
    val primary = MaterialTheme.colorScheme.primary
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(colors.raised), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            Canvas(Modifier.matchParentSize()) { drawCircle(primary.copy(alpha = .28f), radius = size.minDimension * .65f, center = Offset(size.width, 0f)) }
            Column(Modifier.padding(24.dp)) {
                Tag("实时预览")
                Text("让练习空间更像你", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Box(Modifier.padding(top = 24.dp).width(120.dp).height(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun ThemeOption(preset: ThemePreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val swatches = when (preset) {
                ThemePreset.Aurora -> listOf(Color(preset.accent), Color(0xFF121621), Color(0xFF54D7C4))
                ThemePreset.Wood -> listOf(Color(preset.accent), Color(0xFFFFFDF8), Color(0xFF147D73))
                ThemePreset.Glacier -> listOf(Color(preset.accent), Color(0xFFFBFDFF), Color(0xFF147D73))
                ThemePreset.Pulse -> listOf(Color(preset.accent), Color(0xFF1B1F1B), Color(0xFFFFB779))
            }
            swatches.forEach { Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(it)) }
        }
        Text(preset.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Text(preset.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccentOption(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(color))
            .border(if (selected) 4.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = "选择强调色" },
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked, onChecked, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun TunerScreen(database: XStarDatabase, onClose: () -> Unit) {
    val colors = LocalXStarColors.current
    val outline = MaterialTheme.colorScheme.outline
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tuner = remember { AudioTuner(context, scope) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) tuner.start() }
    val reading by tuner.reading.collectAsState()
    fun closeAndSave() {
        scope.launch { database.progressDao().saveLesson(LessonProgressEntity("tuning", "COMPLETED")) }
        onClose()
    }
    DisposableEffect(Unit) { onDispose { tuner.stop() } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 22.dp,
                start = 20.dp, end = 20.dp,
            ),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(::closeAndSave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "关闭调音器") }
                Text("标准调弦", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) tuner.start()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (reading == null) "开始监听" else "监听中") }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("正在调第 6 弦 · 目标 E2", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(reading?.note ?: "—", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 120.sp, lineHeight = 130.sp)
                Canvas(Modifier.fillMaxWidth().widthIn(max = 480.dp).height(140.dp).semantics { contentDescription = "当前声音偏低 12 音分" }) {
                    val rect = Size(size.width * .85f, size.height * 1.5f)
                    drawArc(outline, 200f, 140f, false, Offset(size.width * .075f, size.height * .35f), rect, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    val center = Offset(size.width / 2, size.height)
                    drawLine(colors.attention, center, Offset(size.width * .43f, size.height * .22f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
                val cents = reading?.cents ?: 0f
                Text(if (reading == null) "等待拨弦" else if (kotlin.math.abs(cents) < 5f) "✓ 已调准" else if (cents < 0) "↑ 声音有点低" else "↓ 声音有点高", color = if (kotlin.math.abs(cents) < 5f) colors.steady else colors.attention, style = MaterialTheme.typography.titleLarge)
                Text(reading?.let { "${it.frequency.toInt()} Hz · ${cents.toInt()} cents · 输入清晰" } ?: "点击开始监听，然后拨响一根弦", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("6\nE", "5\nA", "4\nD", "3\nG", "2\nB", "1\nE").forEachIndexed { index, label ->
                    Surface(
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(if (index == 0) 2.dp else 1.dp, if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    ) { Box(Modifier.height(60.dp), contentAlignment = Alignment.Center) { Text(label, fontWeight = FontWeight.Bold) } }
                }
            }
            Text("本地实时分析 · 原始音频不会保存或上传", Modifier.fillMaxWidth().padding(top = 15.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FeatureCard(accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalXStarColors.current
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(if (accent) colors.steadySoft else colors.raised),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(Modifier.padding(22.dp), content = content) }
}

@Composable
private fun SectionTitle(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
        if (trailing.isNotEmpty()) Tag(trailing)
    }
}

@Composable
private fun Tag(text: String, steady: Boolean = false) {
    val colors = LocalXStarColors.current
    Surface(shape = CircleShape, color = if (steady) colors.steadySoft else MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = if (steady) colors.steady else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ResponsiveRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun ToolShortcut(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.width(190.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(18.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ListCard(symbol: String, title: String, subtitle: String, onClick: () -> Unit = {}, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(LocalXStarColors.current.steadySoft), contentAlignment = Alignment.Center) { Text(symbol, color = LocalXStarColors.current.steady, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除 $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SongCard(number: String, title: String, subtitle: String, chords: String, green: Boolean = false, onClick: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(70.dp).height(86.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(if (green) listOf(LocalXStarColors.current.steady, Color(0xFF173F3B)) else listOf(MaterialTheme.colorScheme.primary, Color(0xFF442D6F)))), contentAlignment = Alignment.Center) { Text(number, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 16.dp)) { Tag(if (green) "内置 · 可离线" else "内置 · 学习中", green); Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium); Text(chords, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun PracticeType(symbol: String, title: String, subtitle: String) {
    Card(Modifier.width(190.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(18.dp)) { Text(symbol, fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(28.dp)); Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun LargeTool(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, primary: Boolean) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(if (primary) MaterialTheme.colorScheme.primary else LocalXStarColors.current.raised),
        border = if (primary) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Icon(icon, null, modifier = Modifier.align(Alignment.End).size(38.dp), tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(34.dp)); Text(title, style = MaterialTheme.typography.headlineMedium, color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface); Text(subtitle, color = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onTools: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("XStar Guitar", style = MaterialTheme.typography.headlineLarge)
            Text("下一步，清楚一点", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            Text("从调音开始，按自己的节奏学会第一首歌。无需登录，学习记录保存在这台设备。", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 28.dp))
            Text("调音  →  和弦  →  节奏  →  第一首歌", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 32.dp), shape = RoundedCornerShape(16.dp)) { Text("从零开始") }
            OutlinedButton(onClick = onTools, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(16.dp)) { Text("直接使用工具") }
        }
    }
}

@Composable
private fun PracticeSessionScreen(database: XStarDatabase, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var seconds by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(running) { while (running) { delay(1000); seconds++ } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "退出练习") }; Text("和弦转换练习", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.weight(1f))
            Text("Em  →  G", style = MaterialTheme.typography.displayLarge)
            Text("保持手腕放松，先保证每根弦都能清楚发声", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { running = !running }) { Text(if (running) "暂停" else "继续") }
            OutlinedButton(onClick = { running = false; scope.launch { database.progressDao().savePractice(com.xstar.guitar.data.PracticeSessionEntity(practiceId = "em-g", durationSeconds = seconds, bpm = 60, rating = "SELF_REPORTED")); onClose() } }, modifier = Modifier.padding(top = 10.dp)) { Text("结束并保存") }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SongDetailScreen(onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回曲谱") }; Text("沿着晚风", style = MaterialTheme.typography.titleLarge) }
            Tag("内置 · 可离线", true)
            Text("原创入门练习曲", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
            Text("Em · G · C · D   ·   72 BPM", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("前奏", style = MaterialTheme.typography.titleMedium)
            Text("Em        G\n夜风沿着河岸慢慢走\nC         D\n把今天的故事说给你听", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 20.dp))
            Text("来源：XStar Guitar 原创内容 · 许可：应用内使用", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 28.dp))
        }
    }
}
