package dev.milinko.guitartuner.model

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Čisti (non-Android) helperi za hromatsku detekciju note i korekciju oktavne greške.
 * Namerno odvojeno od Android/Compose koda da bi moglo da se testira običnim JVM unit testovima.
 */

data class ChromaticNote(
    val name: String,      // npr. "E", "F#"
    val octave: Int,       // npr. 2
    val frequency: Float
) {
    val fullName: String get() = "$name$octave"
}

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Frekvencija za dati MIDI broj note (69 = A4) u odnosu na zadatu referentnu frekvenciju A4. */
fun frequencyForMidi(midi: Int, referencePitch: Float = TunerConfigDefaults.REFERENCE_PITCH): Float {
    return referencePitch * Math.pow(2.0, (midi - 69) / 12.0).toFloat()
}

/** Najbliža hromatska (12-tonska, jednako temperovana) nota za datu frekvenciju. */
fun nearestChromaticNote(freqHz: Float, referencePitch: Float = TunerConfigDefaults.REFERENCE_PITCH): ChromaticNote? {
    if (freqHz <= 0f) return null
    val midiFloat = 69.0 + 12.0 * log2(freqHz / referencePitch)
    val midi = midiFloat.roundToInt()
    val name = NOTE_NAMES[((midi % 12) + 12) % 12]
    val octave = (midi / 12) - 1
    return ChromaticNote(name, octave, frequencyForMidi(midi, referencePitch))
}

/**
 * Širok "grid" svih hromatskih nota u opsegu relevantnom za gitaru (C2 do C6),
 * korišćen kao fallback meta za oktavnu korekciju kad sirov pitch nije blizu
 * nijedne note trenutno izabranog štima (npr. korisnik svira potpuno drugu žicu/instrument).
 */
fun chromaticGridFrequencies(referencePitch: Float = TunerConfigDefaults.REFERENCE_PITCH): List<Float> {
    // MIDI 36 (C2) .. 84 (C6)
    return (36..84).map { frequencyForMidi(it, referencePitch) }
}

/**
 * YIN (i slični pitch-detection algoritmi) povremeno pogrešno prijave duplu ili
 * poludeljenu frekvenciju osnovnog tona (oktavna greška), pogotovo na instrumentu
 * bogatom harmonicima kao gitara. Ova funkcija generiše kandidate [pitch, pitch*2, pitch/2]
 * i bira onaj koji je (u centima) najbliži nekoj noti iz očekivanog opsega.
 *
 * @param rawPitch sirov pitch dobijen iz pitch-detection algoritma (Hz)
 * @param primaryTargets frekvencije koje trenutno očekujemo (npr. 6 nota izabranog štima)
 * @param fallbackTargets korišćeno samo ako nijedan kandidat nije razumno blizu primaryTargets
 * @param fallbackThresholdCents ako je najbolji kandidat i dalje dalje od ovoga od primaryTargets,
 *        prelazimo na fallbackTargets (širi hromatski grid)
 */
fun correctOctave(
    rawPitch: Float,
    primaryTargets: List<Float>,
    fallbackTargets: List<Float> = emptyList(),
    fallbackThresholdCents: Float = 200f
): Float {
    if (rawPitch <= 0f) return rawPitch
    val candidates = listOf(rawPitch, rawPitch * 2f, rawPitch / 2f)

    fun bestAgainst(targets: List<Float>): Pair<Float, Float>? {
        if (targets.isEmpty()) return null
        return candidates.map { candidate ->
            val closestCents = targets.minOf { target -> abs(calculateDiffCents(candidate, target)) }
            candidate to closestCents
        }.minByOrNull { it.second }
    }

    val primaryBest = bestAgainst(primaryTargets)
    if (primaryBest != null && primaryBest.second <= fallbackThresholdCents) {
        return primaryBest.first
    }

    val fallbackBest = bestAgainst(fallbackTargets)
    if (fallbackBest != null) {
        return fallbackBest.first
    }

    // Nemamo nijednu referentnu metu - vrati sirov pitch nepromenjen.
    return primaryBest?.first ?: rawPitch
}

/** Podrazumevane vrednosti van TunerConfig (da Chromatic.kt ostane bez Android zavisnosti). */
object TunerConfigDefaults {
    const val REFERENCE_PITCH = 440f
}
