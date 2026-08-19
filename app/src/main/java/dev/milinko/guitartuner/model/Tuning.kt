package dev.milinko.guitartuner.model

import kotlin.math.*

data class Note(
    val name: String,
    val frequency: Float
)

data class Tuning(
    val name: String,
    val notes: List<Note>
)

/**
 * Frekvencije ispod su tabelarne za standardnu referentnu frekvenciju A4 = 440Hz
 * (TunerConfigDefaults.REFERENCE_PITCH). Za drugu kalibraciju (438-445Hz) koristi
 * [Tuning.scaledToReferencePitch] - skaliranje je matematički egzaktno jer su sve
 * note ovde jednako temperovane u odnosu na A440.
 */
object GuitarTunings {
    val ALL_TUNINGS = listOf(
        Tuning("Standard", listOf(
            Note("E2", 82.41f), Note("A2", 110.00f), Note("D3", 146.83f),
            Note("G3", 196.00f), Note("B3", 246.94f), Note("E4", 329.63f)
        )),
        Tuning("Drop D", listOf(
            Note("D2", 73.42f), Note("A2", 110.00f), Note("D3", 146.83f),
            Note("G3", 196.00f), Note("B3", 246.94f), Note("E4", 329.63f)
        )),
        // Napomena: nazivi nota koriste dijeze (#) umesto bemola (b) da bi bili
        // konzistentni sa hromatskom detekcijom (Chromatic.kt), koja uvek prikazuje
        // dijeze - isti fizički ton, samo ujednačen zapis kroz celu aplikaciju.
        Tuning("Half Step Down", listOf(
            Note("D#2", 77.78f), Note("G#2", 103.83f), Note("C#3", 138.59f),
            Note("F#3", 185.00f), Note("A#3", 233.08f), Note("D#4", 311.13f)
        )),
        Tuning("Open G", listOf(
            Note("D2", 73.42f), Note("G2", 98.00f), Note("D3", 146.83f),
            Note("G3", 196.00f), Note("B3", 246.94f), Note("D4", 293.66f)
        )),
        Tuning("DADGAD", listOf(
            Note("D2", 73.42f), Note("A2", 110.00f), Note("D3", 146.83f),
            Note("G3", 196.00f), Note("A3", 220.00f), Note("D4", 293.66f)
        ))
    )
}

/** Skalira sve note u štimu na drugu referentnu frekvenciju A4 (npr. 442Hz umesto 440Hz). */
fun Tuning.scaledToReferencePitch(referencePitch: Float): Tuning {
    if (referencePitch == TunerConfigDefaults.REFERENCE_PITCH) return this
    val ratio = referencePitch / TunerConfigDefaults.REFERENCE_PITCH
    return copy(notes = notes.map { it.copy(frequency = it.frequency * ratio) })
}

data class TuningStatus(
    val frequency: Float = 0f,
    val closestNote: Note? = null,
    val diffCents: Float = 0f,
    /** Hromatski detektovana nota (nezavisna od izabranog štima) - vidi Chromatic.kt. */
    val detectedNote: ChromaticNote? = null
)

fun calculateDiffCents(currentFreq: Float, targetFreq: Float): Float {
    if (currentFreq <= 0f || targetFreq <= 0f) return 0f
    return (1200 * log2(currentFreq / targetFreq)).toFloat()
}
