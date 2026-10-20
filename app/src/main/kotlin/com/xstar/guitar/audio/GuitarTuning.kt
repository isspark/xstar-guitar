package com.xstar.guitar.audio

import kotlin.math.abs
import kotlin.math.log2

data class GuitarString(val number: Int, val note: String, val frequency: Double)

object GuitarTuning {
    val strings = listOf(
        GuitarString(6, "E2", 82.4069), GuitarString(5, "A2", 110.0),
        GuitarString(4, "D3", 146.8324), GuitarString(3, "G3", 195.9977),
        GuitarString(2, "B3", 246.9417), GuitarString(1, "E4", 329.6276),
    )
    fun cents(frequency: Double, string: GuitarString, reference: Int = 440): Float =
        (1200 * log2(frequency / (string.frequency * reference / 440))).toFloat()
    fun nearest(frequency: Double, reference: Int = 440): GuitarString =
        strings.minBy { abs(cents(frequency, it, reference)) }
}
