package com.xstar.guitar.data

enum class PracticeMode { Guided, Beat, Ear }
enum class PracticeLevel(val label: String) { Intro("入门"), Basic("基础"), Intermediate("进阶"), Advanced("高级") }

data class PracticeCategory(val id: String, val symbol: String, val title: String, val description: String)
data class PracticeInstruction(val title: String, val text: String)
data class PracticeExercise(
    val id: String,
    val categoryId: String,
    val title: String,
    val level: PracticeLevel,
    val mode: PracticeMode,
    val goal: String,
    val steps: List<PracticeInstruction>,
    val check: String,
    val tip: String,
    val actions: List<String>,
    val bpm: Int = 50,
    val rounds: Int = 8,
    val chords: List<String> = emptyList(),
    val notes: List<Int> = listOf(40, 43, 47, 52),
    val earType: String = "pitch",
    val songId: String? = null,
    val application: String = "把这个动作放入一小节音乐中，先慢速分解练，再回到完整演奏。",
    val prerequisites: List<String> = emptyList(),
) {
    val summary: String get() = when (mode) {
        PracticeMode.Guided -> "动作指导 · ${steps.size} 项自检"
        PracticeMode.Beat -> "$bpm BPM · $rounds 轮"
        PracticeMode.Ear -> "10 道听辨题 · 可重听"
    }
}

object PracticeCatalog {
    val categories = listOf(
        PracticeCategory("prepare", "○", "吉他与演奏准备", "持琴 · 调弦 · 乐器维护"),
        PracticeCategory("left", "⌁", "左手基础", "按弦 · 爬格子 · 换把"),
        PracticeCategory("right", "P", "右手基础", "拨弦 · PIMA · 分解"),
        PracticeCategory("chord", "G", "单和弦", "开放和弦 · 横按 · 色彩"),
        PracticeCategory("transition", "→", "和弦转换", "双和弦 · 进行 · 声部连接"),
        PracticeCategory("rhythm", "♩", "节奏与拍感", "拍号 · 细分 · 休止"),
        PracticeCategory("strum", "↓↑", "扫弦与拨片", "方向 · 动态 · 风格节奏"),
        PracticeCategory("fingerstyle", "P", "分解与指弹", "低音 · 旋律 · 独立声部"),
        PracticeCategory("percussion", "×", "拍弦与打板", "闷音 · 打击音 · 节奏组合"),
        PracticeCategory("ear", "♫", "听音训练", "高低音 · 音程 · 和弦"),
        PracticeCategory("technique", "✦", "演奏技巧", "拍弦 · 击勾弦 · 滑音"),
        PracticeCategory("theory", "♮", "乐理与读谱", "音程 · 和声 · 视奏"),
        PracticeCategory("fretboard", "◎", "音阶与指板", "找音 · CAGED · 把位连接"),
        PracticeCategory("performance", "♫", "综合演奏", "弹唱 · 段落 · 完整演奏"),
        PracticeCategory("creation", "+", "即兴与创作", "问答 · 旋律 · 编配"),
        PracticeCategory("management", "≡", "演奏管理", "练习计划 · 排练 · 演出准备"),
    )
    private fun steps(vararg values: Pair<String, String>) = values.map { PracticeInstruction(it.first, it.second) }
    private fun chordExercise(name: String) = PracticeExercise(
        "chord.$name", "chord", "$name 和弦发声", PracticeLevel.Intro, PracticeMode.Guided,
        "按好 $name，并逐弦确认应发声的琴弦清楚、不碰到邻弦。",
        steps("查看指法" to "打开下方和弦指法，核对手指编号、空弦和不弹弦。",
            "逐弦拨响" to "从最低的可弹弦开始逐根拨弦；指尖靠近品丝后方，不压在品丝上。",
            "松开再按" to "放松左手，再次摆出手型。重复 5 次，每次检查杂音。"),
        "应发声的琴弦都清楚；不弹弦没有被误扫。", "杂音时先移动指尖位置，再略加压力；不要用整只手死捏琴颈。",
        listOf(name), chords = listOf(name),
    )
    private fun transition(id: String, names: List<String>, level: PracticeLevel, tempo: Int) = PracticeExercise(
        id, "transition", names.joinToString(" → "), level, PracticeMode.Beat,
        "在 $tempo BPM 下，每 4 拍换一个和弦，连续完成 8 轮和弦进行。",
        steps("先分别按响" to "逐个确认 ${names.joinToString("、")} 的发声。先听示范，再无节拍切换手型。",
            "缩小动作" to "观察共同手指，减少抬指高度；不需要固定所有手指同时落下。",
            "听预备拍" to "先听 4 拍；随后在每小节第一拍换和弦。换弦动作在前一拍末提前准备。"),
        "完成 8 轮并自评节奏稳定，换和弦时没有长时间停顿。", "跟不上时降低 5 BPM；仍有杂音则先回到单和弦自检。",
        names, tempo, chords = names, songId = "wei_ni_liu_yi_zhan_deng",
    )
    val exercises: List<PracticeExercise> = buildList {
        add(PracticeExercise("left.single", "left", "单指按弦", PracticeLevel.Intro, PracticeMode.Guided,
            "用食指按响 1 弦第 1 品，放松后重复 5 次。",
            steps("找到位置" to "1 弦是最细的弦。指尖落在第 1 品内、靠近品丝后方。", "检查声音" to "右手轻拨，听是否清楚；食指不要碰到 2 弦。", "释放压力" to "保持触弦但不压弦，再重新按下 5 次。"),
            "音清楚，手腕自然，无关节疼痛。", "手腕不要折成直角；有刺痛或麻木时立即休息。", listOf("1 弦 1 品"), notes = listOf(65)))
        add(PracticeExercise("left.chromatic", "left", "1-2-3-4 爬格子", PracticeLevel.Basic, PracticeMode.Beat,
            "在 50 BPM 下，一拍一个音，完成 8 轮单弦指法。",
            steps("固定把位" to "1 弦第 5、6、7、8 品分别用食、中、无名、小指。", "交替拨弦" to "每拍弹一个音，手指靠近琴弦；不要追求最大张开。", "保持清楚" to "按 5→6→7→8 循环，每个音长度均匀。"),
            "每个音清楚，四拍长度相同。", "先在第 5 品练习以减少伸展，放慢比使劲更有效。",
            listOf("5 品 · 食指", "6 品 · 中指", "7 品 · 无名指", "8 品 · 小指"), notes = listOf(69,70,71,72)))
        add(PracticeExercise("left.shift", "left", "换把与定位", PracticeLevel.Intermediate, PracticeMode.Beat,
            "一小节在第 5 品，一小节在第 7 品，完成 8 轮定位。",
            steps("建立起点" to "食指按 1 弦第 5 品，保持手腕自然。", "整手移动" to "下一个小节将手移动到第 7 品；拇指跟随手掌移动，不夹紧琴颈。", "落点检查" to "换把后落在品丝后方，先求准再逐步减少看指板的次数。"),
            "换把后能直接清楚发声，不来回找品。", "移动时稍减按弦压力，避免摩擦和无意识紧绷。", listOf("1 弦 5 品", "1 弦 7 品"), notes = listOf(69,71)))
        add(PracticeExercise("right.thumb", "right", "拇指低音拨弦", PracticeLevel.Intro, PracticeMode.Beat,
            "四拍依次拨 6、5、4、5 弦，完成 8 轮。",
            steps("摆好右手" to "手腕自然，拇指略在其他手指前方，别夹进掌心。", "轻拨低音" to "拇指依次拨 6、5、4、5 弦；每拍一根，不触碰邻弦。", "听力度" to "四个音尽量均匀，不随弦号改变而突然加大音量。"),
            "每拍只有目标弦发声，音量大致均匀。", "动作来自拇指自然运动，不用整个前臂甩动。", listOf("P · 6 弦", "P · 5 弦", "P · 4 弦", "P · 5 弦"), notes = listOf(40,45,50,45)))
        add(PracticeExercise("right.pima", "right", "PIMA 四音分解", PracticeLevel.Basic, PracticeMode.Beat,
            "拇指、食指、中指、无名指依次拨 6、3、2、1 弦。",
            steps("认识 PIMA" to "P 是拇指，i 是食指，m 是中指，a 是无名指。", "分配琴弦" to "P 拨 6 弦，i 拨 3 弦，m 拨 2 弦，a 拨 1 弦；先用空弦。", "均匀循环" to "一拍一个音，拨完回到自然位置，不把手指用力勾入掌心。"),
            "4 个音时值均匀，手掌基本不晃动。", "高音突然变重时降低力度，先让四指音量接近。", listOf("P · 6 弦", "i · 3 弦", "m · 2 弦", "a · 1 弦"), notes = listOf(40,55,59,64)))
        add(PracticeExercise("right.alternating", "right", "食中指交替拨弦", PracticeLevel.Basic, PracticeMode.Beat,
            "在 1 弦空弦上 i-m 交替，完成 8 轮。",
            steps("选定一根弦" to "在 1 弦空弦上练，不需要左手。", "交替" to "食指 i、中指 m 依次拨，每拍一次，避免连续使用同一根手指。", "听均匀" to "两指发出的声音和音量尽量相近。"),
            "i、m 连续交替且拍子稳定。", "手指离弦太远时先减慢，练习小动作。", listOf("i · 1 弦", "m · 1 弦", "i · 1 弦", "m · 1 弦"), notes = listOf(64,64,64,64)))
        listOf("Em", "Am", "C", "G", "D", "E", "A", "Dm").forEach { add(chordExercise(it)) }
        add(transition("em-g", listOf("Em", "G"), PracticeLevel.Basic, 50))
        add(transition("chord.am-c", listOf("Am", "C"), PracticeLevel.Basic, 50))
        add(transition("chord.g-d", listOf("G", "D"), PracticeLevel.Basic, 50))
        add(transition("chord.loop", listOf("G", "D", "Em", "C"), PracticeLevel.Intermediate, 60))
        add(PracticeExercise("chord.barre", "chord", "F 横按准备", PracticeLevel.Intermediate, PracticeMode.Guided,
            "先用食指横按 1、2 弦第 1 品，再逐步加入 F 小横按指法。",
            steps("减少负担" to "先只按 1、2 弦第 1 品，食指略侧，用较平整的一面接触琴弦。", "逐弦检查" to "食指不要用关节凹槽压弦，分别拨响两根弦。", "加入指法" to "打开 F 和弦图核对完整指法。先分次练，每次短暂按住后放松。"),
            "两根弦能清楚发声，手腕和拇指不疼痛。", "横按不是靠死捏，先调整接触位置；出现关节疼痛就停止。", listOf("小横按"), chords = listOf("F")))
        add(PracticeExercise("rhythm.down", "rhythm", "四分音符下扫", PracticeLevel.Intro, PracticeMode.Beat,
            "4/4 拍每拍一次下扫，连续完成 8 轮。",
            steps("先闷弦" to "左手轻搭弦上不压弦，用闷音练节奏。", "下扫" to "每拍从低音弦扫向高音弦；手腕自然摆动。", "加入 Em" to "动作稳定后按 Em，保持每拍一次。"),
            "一拍一下，不抢拍；手腕自然。", "先只轻扫少量琴弦，不必每次用力扫满六根。", listOf("↓", "↓", "↓", "↓"), 60, chords = listOf("Em")))
        add(PracticeExercise("rhythm.eighth", "rhythm", "八分音符上下扫", PracticeLevel.Basic, PracticeMode.Beat,
            "每拍下上各一次，口数 1 & 2 & 3 & 4 &。",
            steps("建立拍感" to "下扫对应数字，上扫对应 &，先轻搭琴弦练闷音。", "持续摆动" to "手腕连续下上摆动；每拍两次，不能把上扫挤在下一拍之前。", "加上和弦" to "按 Em；上扫触及高音弦即可，不必扫满六根。"),
            "每拍两次且均匀，连续 8 轮。", "跟不上时降速，不要为了上扫而突然加大动作。", listOf("↓  ↑", "↓  ↑", "↓  ↑", "↓  ↑"), 60, chords = listOf("Em")))
        add(PracticeExercise("rhythm.rest", "rhythm", "休止与消音", PracticeLevel.Intermediate, PracticeMode.Beat,
            "第 1、3 拍下扫，第 2、4 拍消音，保留内心数拍。",
            steps("数四拍" to "休止也占时间，不能因为不弹就跳过。", "控制尾音" to "第 2、4 拍用右手轻触琴弦停止声音。", "保持运动" to "手腕动作与口数不中断，消音时别撞击琴体。"),
            "休止位置安静，下一拍准时进入。", "只用足以停止振动的接触力度。", listOf("↓", "休止", "↓", "休止"), 60, chords = listOf("Em")))
        add(PracticeExercise("ear.pitch", "ear", "听辨高低与相同", PracticeLevel.Intro, PracticeMode.Ear,
            "听两个先后发出的音，判断第二个音更高、更低还是相同。",
            steps("听先后" to "先听第一个音，再比较第二个音；忽略音量，只判断音高。", "可以重听" to "不确定时重听，不需要绝对音感或知道音名。", "查看解析" to "答题后可以听答案，并在下一题继续。"),
            "完成 10 题，查看自己的正确率。", "音高不是音量；把注意力放在声音的高低走向。", listOf("听音")))
        add(PracticeExercise("ear.interval", "ear", "听辨常见音程", PracticeLevel.Intermediate, PracticeMode.Ear,
            "比较两个上行音，区分大三度、纯五度和八度。",
            steps("认识距离" to "大三度相隔 4 半音，纯五度 7 半音，八度 12 半音。", "听距离" to "每题会依次播放两个音，先听相对距离，不猜音名。", "对照答案" to "答后查看半音数，再重听体会跨度。"),
            "完成 10 题并复听错题。", "先比较跨度大小；八度音名相同，但第二个音更高。", listOf("音程"), earType = "interval"))
        add(PracticeExercise("ear.quality", "ear", "大三与小三和弦", PracticeLevel.Basic, PracticeMode.Ear,
            "听同时发出的三和弦，区分大三和弦与小三和弦。",
            steps("听整体色彩" to "大三和弦为根音、大三度、纯五度，小三和弦中间音降低半音。", "不只靠情绪" to "明亮或柔和只作辅助，重点听中间音与根音的距离。", "重听答案" to "每题可重复播放，作答后核对组成音。"),
            "完成 10 题并查看正确率。", "使用相同的音量、音色比较，避免把响度当成性质。", listOf("和弦听辨"), earType = "quality"))
        add(PracticeExercise("tech.tap", "technique", "空弦拍弦", PracticeLevel.Intermediate, PracticeMode.Beat,
            "第 1、3 拍轻下扫，第 2、4 拍轻拍闷弦，完成 8 轮。",
            steps("先闷弦" to "左手轻搭琴弦不按到品丝。", "轻拍" to "右手以舒适的小动作触弦，获得短促打击音；不敲琴桥或猛拍琴面。", "组合" to "第 1、3 拍下扫，第 2、4 拍拍弦，强弱均匀。"),
            "拍弦位置清楚，手腕无不适。", "只需要短促轻触；疼痛说明动作或力度需要调整。", listOf("↓", "× 拍弦", "↓", "× 拍弦"), 50))
        add(PracticeExercise("tech.hammer", "technique", "击弦与勾弦", PracticeLevel.Intermediate, PracticeMode.Guided,
            "在 1 弦第 5、7 品之间完成 5→7 击弦与 7→5 勾弦。",
            steps("准备底音" to "食指按第 5 品，右手先拨响。", "击弦" to "无名指迅速落在第 7 品品丝后方，让第二个音发声，不再拨弦。", "勾弦" to "食指保持第 5 品，无名指轻向掌心方向带弦离开，让第 5 品继续发声。"),
            "两个音都听得清楚，节奏均匀且没有用力甩手。", "先慢速做单次动作，别把勾弦做成单纯垂直抬指。", listOf("5 → 7 → 5"), notes = listOf(69,71,69)))
        add(PracticeExercise("tech.slide", "technique", "同弦滑音", PracticeLevel.Intermediate, PracticeMode.Guided,
            "在 1 弦第 5 品拨响后滑向第 7 品，听到连续音高变化。",
            steps("拨响起音" to "食指或无名指按第 5 品，右手拨响。", "保持接触" to "在同一根弦上滑到第 7 品，维持适量压力，不抬指。", "停准落点" to "在第 7 品品丝后方停稳，听终点音；先慢做单次。"),
            "终点清楚，不越过目标品。", "用轻而连续的接触，若摩擦不适立即停止。", listOf("5 / 7"), notes = listOf(69,71)))
        add(PracticeExercise("tech.harmonic", "technique", "十二品自然泛音", PracticeLevel.Advanced, PracticeMode.Guided,
            "在第 12 品品丝正上方轻触并拨响自然泛音。",
            steps("找准节点" to "左手轻触 1 弦第 12 品品丝正上方，不压到指板。", "拨响后离开" to "右手拨弦的瞬间，左手轻轻离弦。", "对比" to "与同弦空弦比较，泛音应高一个八度且更轻亮。"),
            "能重复获得轻亮泛音，不是普通按弦音或闷音。", "轻触的位置比力度更重要，先微调品丝正上方的落点。", listOf("12 品 · 轻触"), notes = listOf(76)))
    }.map { e -> when (e.id) {
        "rhythm.down", "rhythm.eighth" -> e.copy(categoryId = "strum")
        "tech.tap" -> e.copy(categoryId = "percussion")
        else -> e
    } } + ExtendedPracticeContent.exercises
    fun find(id: String): PracticeExercise? = exercises.find { it.id == id }
}

data class EarQuestion(val notes: List<Int>, val answer: Int, val explanation: String, val simultaneous: Boolean = false)

object EarTraining {
    fun options(type: String) = when (type) {
        "quality" -> listOf("大三和弦", "小三和弦")
        "interval" -> listOf("大三度", "纯五度", "八度")
        else -> listOf("第二个更高", "第二个更低", "两个相同")
    }
    fun question(type: String, index: Int, seed: Int): EarQuestion {
        val random = kotlin.random.Random(seed + index * 101)
        val root = random.nextInt(48, 61)
        val answer = random.nextInt(options(type).size)
        return when (type) {
            "quality" -> EarQuestion(listOf(root, root + if (answer == 0) 4 else 3, root + 7), answer,
                if (answer == 0) "根音 + 4 半音 + 7 半音，是大三和弦。" else "根音 + 3 半音 + 7 半音，是小三和弦。", true)
            "interval" -> {
                val semitones = listOf(4,7,12)[answer]
                EarQuestion(listOf(root, root + semitones), answer, "第二个音比第一个高 $semitones 半音，是${options(type)[answer]}。")
            }
            else -> {
                val difference = random.nextInt(2, 8)
                EarQuestion(listOf(root, root + when (answer) { 0 -> difference; 1 -> -difference; else -> 0 }), answer,
                    when (answer) { 0 -> "第二个音更高，上行 $difference 半音。"; 1 -> "第二个音更低，下行 $difference 半音。"; else -> "两次播放的是同一音高。" })
            }
        }
    }
}
