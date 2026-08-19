package dev.milinko.guitartuner.model

import dev.milinko.guitartuner.audio.TunerConfig
import kotlin.math.abs

/**
 * Čist (non-Android) pipeline za obradu sirovog pitch signala:
 *
 *   sirov pitch -> (1) octave-error korekcija -> (2) cents-bazirani jump filter
 *   -> (3) jedan adaptivni EMA -> (4) note lock histerezis -> [TuningStatus]
 *
 * Namerno odvojeno od TunerViewModel (koji zavisi od Android/AndroidViewModel) da bi
 * moglo da se testira običnim JVM unit testovima bez Robolectric-a.
 */
class PitchStabilizer {

    private var smoothedPitch = 0f
    private var lastStablePitch = 0f
    private var jumpCounter = 0
    private var lockedNote: Note? = null
    private var detectionStartTime = 0L

    /** True dok pipeline "prati" aktivan signal (koristi se da se zna kad treba reset). */
    val isActive: Boolean get() = smoothedPitch != 0f

    /**
     * Obradi jedan sirov pitch frejm.
     *
     * @return novi [TuningStatus] ako ima šta da se prikaže, ili null ako frejm treba
     *         ignorisati (attack faza posle tišine, ili sumnjiv skok koji još nije potvrđen).
     */
    fun process(
        rawPitch: Float,
        currentTimeMs: Long,
        targetNotes: List<Note>,
        fallbackTargets: List<Float>,
        referencePitch: Float
    ): TuningStatus? {
        if (rawPitch <= 0f || targetNotes.isEmpty()) return null

        val targetFrequencies = targetNotes.map { it.frequency }

        // Prvi validan frejm posle tišine - zabeleži vreme, ali ne šalji još u UI
        // (izbegava da igla "poleti" tokom prvih par ms udara u žicu). I baznu vrednost
        // provlačimo kroz octave-correction da eventualan harmonik na samom udaru žice
        // ne postane loša polazna tačka za EMA.
        if (smoothedPitch == 0f) {
            detectionStartTime = currentTimeMs
            smoothedPitch = correctOctave(rawPitch, targetFrequencies, fallbackTargets)
            return null
        }
        if (currentTimeMs - detectionStartTime < TunerConfig.ATTACK_IGNORE_MS) return null

        // 1. OCTAVE CORRECTION - "lepljivo" za već zaključanu žicu.
        // Dok pratimo aktivnu žicu, prvo probamo da protumačimo sirov pitch ISKLJUČIVO u
        // odnosu na tu žicu (uz generoznu toleranciju od LOCKED_NOTE_SEARCH_WINDOW_CENTS).
        // Ovo sprečava da harmonik ili šum (npr. zvuk civije) tokom bledenja tona odvuku
        // detekciju na potpuno drugu žicu - žice su realno razmaknute >380 centi, pa 150
        // centi ostaje bezbedna margina. Pun pretres svih žica (+ hromatski grid) se radi
        // samo kad nemamo trenutno zaključanu notu (svež početak/stvarna promena žice).
        val locked = lockedNote
        val correctedPitch = if (locked != null) {
            val stickyCandidate = correctOctave(rawPitch, listOf(locked.frequency), emptyList())
            val stickyDistanceCents = abs(calculateDiffCents(stickyCandidate, locked.frequency))
            if (stickyDistanceCents <= TunerConfig.LOCKED_NOTE_SEARCH_WINDOW_CENTS) {
                stickyCandidate
            } else {
                correctOctave(rawPitch, targetFrequencies, fallbackTargets)
            }
        } else {
            correctOctave(rawPitch, targetFrequencies, fallbackTargets)
        }

        // 2. CENTS-BAZIRAN JUMP FILTER
        if (lastStablePitch > 0) {
            val jumpCents = abs(calculateDiffCents(correctedPitch, lastStablePitch))
            if (jumpCents > TunerConfig.JUMP_THRESHOLD_CENTS) {
                jumpCounter++
                if (jumpCounter < TunerConfig.MAX_JUMPS) {
                    return null // sumnjiv skok - sačekaj da se ponovi pre nego što poveruješ
                }
            } else {
                jumpCounter = 0
            }
        }
        lastStablePitch = correctedPitch

        // 3. JEDAN ADAPTIVNI EMA (alpha po veličini skoka u centima)
        val delta = correctedPitch - smoothedPitch
        val deltaCents = abs(calculateDiffCents(correctedPitch, smoothedPitch))
        val alpha = when {
            deltaCents > 400f -> 0.8f
            deltaCents > 80f -> 0.3f
            else -> 0.15f
        }
        smoothedPitch += alpha * delta

        // 4. NOTE LOCK (histerezis)
        val note = getStableNote(smoothedPitch, targetNotes)
        var diffCents = calculateDiffCents(smoothedPitch, note.frequency)
        if (abs(diffCents) < TunerConfig.DEAD_ZONE_CENTS) diffCents = 0f

        // 5. Hromatska detekcija - stvarno svirana nota, nezavisno od izabranog štima
        val chromaticNote = nearestChromaticNote(smoothedPitch, referencePitch)

        return TuningStatus(
            frequency = smoothedPitch,
            closestNote = note,
            diffCents = diffCents.coerceIn(-50f, 50f),
            detectedNote = chromaticNote
        )
    }

    fun reset() {
        smoothedPitch = 0f
        lastStablePitch = 0f
        jumpCounter = 0
        lockedNote = null
    }

    private fun getStableNote(pitch: Float, targetNotes: List<Note>): Note {
        val closest = findClosestNote(pitch, targetNotes)
        if (lockedNote == null) {
            lockedNote = closest
        }
        val diff = abs(calculateDiffCents(pitch, lockedNote!!.frequency))
        if (diff > TunerConfig.NOTE_LOCK_HYSTERESIS_CENTS) {
            lockedNote = closest
        }
        return lockedNote!!
    }

    private fun findClosestNote(pitch: Float, targetNotes: List<Note>): Note {
        return targetNotes.minByOrNull { abs(calculateDiffCents(pitch, it.frequency)) } ?: targetNotes.first()
    }
}
