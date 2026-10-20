package com.xstar.guitar.data

data class ChordType(
    val id: String, val suffix: String, val name: String, val shortName: String,
    val intervals: List<Int>, val roles: List<String>,
    val e: List<Int>, val ef: List<Int>, val eb: List<Int>,
    val a: List<Int>, val af: List<Int>, val ab: List<Int>,
)

data class GuitarChord(val root: String, val pitchClass: Int, val type: ChordType) {
    val id: String get() = root + type.suffix
    val tones: List<String> get() = type.intervals.map { ChordLibrary.roots[(pitchClass + it) % 12] }
}

data class ChordVoicing(val frets: List<Int>, val fingers: List<Int>, val name: String, val shape: String, val easy: Boolean) {
    val startFret: Int get() = if (frets.max() <= 5) 1 else frets.filter { it > 0 }.min()
    val soundingStrings: List<Int> get() = frets.indices.filter { frets[it] >= 0 }
    fun fingerInstruction(finger: Int): String {
        val indices = frets.indices.filter { frets[it] > 0 && fingers[it] == finger }
        if (indices.isEmpty()) return "这套指法不需要用到这根手指。"
        if (indices.size > 1 && indices.map { frets[it] }.distinct().size == 1) {
            return "在第 ${frets[indices.first()]} 品横按 ${6 - indices.first()} 弦到 ${6 - indices.last()} 弦；指侧发力，其他手指拱起。"
        }
        return indices.joinToString("，") { "按住 ${6 - it} 弦第 ${frets[it]} 品" } + "，指尖靠近品丝后方。"
    }
}

enum class PickingStyle(val label: String, val hint: String) {
    Down("↓ 下扫", "拨片／拇指 · 从低音弦扫向高音弦"),
    Up("↑ 上扫", "拨片／手指 · 从高音弦扫向低音弦"),
    Pima("P i m a 分解", "P 拇指 · i 食指 · m 中指 · a 无名指"),
}

data class PluckEvent(val stringIndex: Int, val midiNote: Int, val finger: String, val offsetMillis: Int)

object ChordLibrary {
    val roots = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    val tuning = listOf(40, 45, 50, 55, 59, 64)
    val fingerNames = listOf("", "食指", "中指", "无名指", "小指")
    private fun n(vararg values: Int) = values.toList()
    val types = listOf(
        ChordType("major", "", "大三和弦", "大三", n(0,4,7), listOf("根音","大三度","纯五度"), n(0,2,2,1,0,0),n(0,2,3,1,0,0),n(1,3,4,2,1,1),n(-1,0,2,2,2,0),n(0,0,1,2,3,0),n(0,1,2,3,4,1)),
        ChordType("minor", "m", "小三和弦", "小三", n(0,3,7), listOf("根音","小三度","纯五度"), n(0,2,2,0,0,0),n(0,2,3,0,0,0),n(1,3,4,1,1,1),n(-1,0,2,2,1,0),n(0,0,2,3,1,0),n(0,1,3,4,2,1)),
        ChordType("7", "7", "属七和弦", "属七", n(0,4,7,10), listOf("根音","大三度","纯五度","小七度"), n(0,2,0,1,0,0),n(0,2,0,1,0,0),n(1,3,1,2,1,1),n(-1,0,2,0,2,0),n(0,0,1,0,2,0),n(0,1,3,1,4,1)),
        ChordType("maj7", "maj7", "大七和弦", "大七", n(0,4,7,11), listOf("根音","大三度","纯五度","大七度"), n(0,2,1,1,0,0),n(0,3,1,2,0,0),n(1,4,2,3,1,1),n(-1,0,2,1,2,0),n(0,0,2,1,3,0),n(0,1,3,2,4,1)),
        ChordType("m7", "m7", "小七和弦", "小七", n(0,3,7,10), listOf("根音","小三度","纯五度","小七度"), n(0,2,0,0,0,0),n(0,2,0,0,0,0),n(1,3,1,1,1,1),n(-1,0,2,0,1,0),n(0,0,2,0,1,0),n(0,1,3,1,2,1)),
        ChordType("sus2", "sus2", "挂二和弦", "挂二", n(0,2,7), listOf("根音","大二度","纯五度"), n(0,2,4,4,0,0),n(0,1,3,4,0,0),n(1,2,3,4,1,1),n(-1,0,2,2,0,0),n(0,0,1,2,0,0),n(0,1,3,4,1,1)),
        ChordType("sus4", "sus4", "挂四和弦", "挂四", n(0,5,7), listOf("根音","纯四度","纯五度"), n(0,2,2,2,0,0),n(0,1,2,3,0,0),n(1,2,3,4,1,1),n(-1,0,2,2,3,0),n(0,0,1,2,3,0),n(0,1,2,3,4,1)),
        ChordType("dim", "dim", "减三和弦", "减三", n(0,3,6), listOf("根音","小三度","减五度"), n(-1,-1,2,3,5,3),n(0,0,1,2,4,3),n(0,0,1,2,4,3),n(-1,0,1,2,1,-1),n(0,0,1,3,2,0),n(0,1,2,4,3,0)),
        ChordType("aug", "aug", "增三和弦", "增三", n(0,4,8), listOf("根音","大三度","增五度"), n(0,3,2,1,1,0),n(0,4,3,1,2,0),n(1,4,3,2,2,1),n(-1,0,3,2,2,1),n(0,0,4,2,3,1),n(0,1,4,3,3,2)),
        ChordType("5", "5", "强力和弦", "强力", n(0,7), listOf("根音","纯五度"), n(0,2,2,-1,-1,-1),n(0,1,2,0,0,0),n(1,3,4,0,0,0),n(-1,0,2,2,-1,-1),n(0,0,1,2,0,0),n(0,1,3,4,0,0)),
    )
    val chords = roots.flatMapIndexed { pc, root -> types.map { GuitarChord(root, pc, it) } }
    val popular = setOf("C", "D", "E", "G", "A", "Am", "Em", "Dm", "F", "Cmaj7", "D7", "G7")
    private val openShapes = mapOf(
        "C" to (n(-1,3,2,0,1,0) to n(0,3,2,0,1,0)), "Cmaj7" to (n(-1,3,2,0,0,0) to n(0,3,2,0,0,0)), "C7" to (n(-1,3,2,3,1,0) to n(0,3,2,4,1,0)),
        "D" to (n(-1,-1,0,2,3,2) to n(0,0,0,1,3,2)), "Dm" to (n(-1,-1,0,2,3,1) to n(0,0,0,2,3,1)), "D7" to (n(-1,-1,0,2,1,2) to n(0,0,0,2,1,3)),
        "Dmaj7" to (n(-1,-1,0,2,2,2) to n(0,0,0,1,1,1)), "Dm7" to (n(-1,-1,0,2,1,1) to n(0,0,0,2,1,1)), "Dsus2" to (n(-1,-1,0,2,3,0) to n(0,0,0,1,3,0)), "Dsus4" to (n(-1,-1,0,2,3,3) to n(0,0,0,1,3,4)),
        "G" to (n(3,2,0,0,0,3) to n(2,1,0,0,0,3)), "G7" to (n(3,2,0,0,0,1) to n(3,2,0,0,0,1)), "Gmaj7" to (n(3,2,0,0,0,2) to n(3,1,0,0,0,2)),
    )
    private val voicingCache = chords.associate { chord ->
        val t = chord.type
        val variants = mutableListOf<ChordVoicing>()
        openShapes[chord.id]?.let { (frets, fingers) -> variants += ChordVoicing(frets, fingers, "开放指法", "开放", true) }
        listOf("E" to 4, "A" to 9).forEach { (shape, base) ->
            val shift = (chord.pitchClass - base + 12) % 12
            val template = if (shape == "E") t.e else t.a
            val fingers = if (shape == "E") { if (shift == 0) t.ef else t.eb } else { if (shift == 0) t.af else t.ab }
            val frets = template.map { if (it < 0) -1 else it + shift }
            variants += ChordVoicing(frets, fingers, if (shift == 0) "开放指法" else "$shape 型 · ${frets.filter { it >= 0 }.min()} 品", shape, shift == 0 && t.id !in listOf("dim", "aug", "sus2"))
        }
        chord.id to variants.sortedWith(compareByDescending<ChordVoicing> { it.easy }.thenBy { it.frets.max() })
    }
    fun voicings(chord: GuitarChord): List<ChordVoicing> = voicingCache.getValue(chord.id)
    fun normalize(value: String): String = value.trim().replace("♯", "#").replace("♭", "b").replace(Regex("\\s+"), "")
    fun search(query: String): List<GuitarChord> {
        val normalized = normalize(query)
        return chords.filter { normalize(it.id).startsWith(normalized) || it.type.name.contains(normalized) }
    }
    fun sequence(voicing: ChordVoicing, style: PickingStyle): List<PluckEvent> {
        val strings = voicing.soundingStrings
        val steps = when (style) {
            PickingStyle.Down -> strings.map { it to "↓ 下扫" }
            PickingStyle.Up -> strings.reversed().map { it to "↑ 上扫" }
            PickingStyle.Pima -> {
                val treble = strings.drop(1).takeLast(3)
                val fingers = listOf("i 食指", "m 中指", "a 无名指")
                listOf(strings.first() to "P 拇指") + treble.mapIndexed { i, s -> s to fingers[i] } + treble.dropLast(1).reversed().map { it to fingers[treble.indexOf(it)] }
            }
        }
        return steps.mapIndexed { i, (string, finger) -> PluckEvent(string, tuning[string] + voicing.frets[string], finger, i * if (style == PickingStyle.Pima) 320 else 55) }
    }
}
