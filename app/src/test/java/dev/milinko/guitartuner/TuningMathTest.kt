package dev.milinko.guitartuner

import dev.milinko.guitartuner.audio.TunerConfig
import dev.milinko.guitartuner.model.Note
import dev.milinko.guitartuner.model.PitchStabilizer
import dev.milinko.guitartuner.model.calculateDiffCents
import dev.milinko.guitartuner.model.chromaticGridFrequencies
import dev.milinko.guitartuner.model.correctOctave
import dev.milinko.guitartuner.model.nearestChromaticNote
import dev.milinko.guitartuner.model.TuningStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * JVM unit testovi za "čistu" (non-Android) matematiku tjunera - ne zahtevaju
 * Robolectric ni Android uređaj, pokreću se sa `./gradlew testDebugUnitTest`.
 */
class TuningMathTest {

    private fun assertCentsEquals(expected: Float, actual: Float, toleranceCents: Float = 2f) {
        assertTrue(
            "Očekivano ~$expected cents, dobijeno $actual cents",
            abs(expected - actual) <= toleranceCents
        )
    }

    // --- calculateDiffCents ---

    @Test
    fun diffCents_sameFrequency_isZero() {
        assertCentsEquals(0f, calculateDiffCents(440f, 440f))
    }

    @Test
    fun diffCents_oneOctaveUp_is1200Cents() {
        assertCentsEquals(1200f, calculateDiffCents(880f, 440f))
    }

    @Test
    fun diffCents_twoOctavesDown_isMinus2400Cents() {
        // A2 (110Hz) je dve oktave ispod A4 (440Hz)
        assertCentsEquals(-2400f, calculateDiffCents(110f, 440f))
    }

    @Test
    fun diffCents_invalidFrequency_isZero() {
        assertEquals(0f, calculateDiffCents(0f, 440f))
        assertEquals(0f, calculateDiffCents(440f, -1f))
    }

    // --- nearestChromaticNote ---

    @Test
    fun chromaticNote_A4_standardReference() {
        val note = nearestChromaticNote(440f, referencePitch = 440f)
        assertNotNull(note)
        assertEquals("A", note!!.name)
        assertEquals(4, note.octave)
    }

    @Test
    fun chromaticNote_lowE2() {
        val note = nearestChromaticNote(82.41f, referencePitch = 440f)
        assertNotNull(note)
        assertEquals("E", note!!.name)
        assertEquals(2, note.octave)
    }

    @Test
    fun chromaticNote_highE4() {
        val note = nearestChromaticNote(329.63f, referencePitch = 440f)
        assertNotNull(note)
        assertEquals("E", note!!.name)
        assertEquals(4, note.octave)
    }

    @Test
    fun chromaticNote_customReferencePitch_442() {
        // Kad je referentna frekvencija A4=442Hz, 442Hz treba da se prepozna kao A4 (0 cents off).
        val note = nearestChromaticNote(442f, referencePitch = 442f)
        assertNotNull(note)
        assertEquals("A", note!!.name)
        assertEquals(4, note.octave)
    }

    @Test
    fun chromaticNote_silentSignal_isNull() {
        assertNull(nearestChromaticNote(0f))
        assertNull(nearestChromaticNote(-5f))
    }

    // --- correctOctave (oktavna korekcija YIN grešaka) ---

    @Test
    fun correctOctave_doubledPitch_getsHalvedToTarget() {
        // YIN je pogrešno prijavio duplu frekvenciju za E2 (82.41Hz -> ~164.8Hz)
        val corrected = correctOctave(rawPitch = 164.82f, primaryTargets = listOf(82.41f))
        assertCentsEquals(0f, calculateDiffCents(corrected, 82.41f), toleranceCents = 5f)
    }

    @Test
    fun correctOctave_halvedPitch_getsDoubledToTarget() {
        // YIN je pogrešno prijavio poludeljenu frekvenciju za E2 (82.41Hz -> ~41.2Hz)
        val corrected = correctOctave(rawPitch = 41.2f, primaryTargets = listOf(82.41f))
        assertCentsEquals(0f, calculateDiffCents(corrected, 82.41f), toleranceCents = 5f)
    }

    @Test
    fun correctOctave_alreadyCorrect_staysUnchanged() {
        val corrected = correctOctave(rawPitch = 82.41f, primaryTargets = listOf(82.41f))
        assertCentsEquals(0f, calculateDiffCents(corrected, 82.41f), toleranceCents = 1f)
    }

    @Test
    fun correctOctave_farFromPrimary_fallsBackToChromaticGrid() {
        // Nijedna primarna meta nije blizu - treba da padne na hromatski grid i i dalje
        // izabere razuman kandidat (najbliži nekoj noti u gridu), a ne sirov, nekorigovan pitch.
        val fallback = chromaticGridFrequencies(referencePitch = 440f)
        val corrected = correctOctave(
            rawPitch = 987.77f, // B5, daleko od gitarskih target nota
            primaryTargets = listOf(82.41f, 110.00f),
            fallbackTargets = fallback
        )
        val nearest = fallback.minByOrNull { abs(calculateDiffCents(corrected, it)) }
        assertNotNull(nearest)
        assertCentsEquals(0f, calculateDiffCents(corrected, nearest!!), toleranceCents = 5f)
    }

    // --- PitchStabilizer (ceo pipeline: attack phase, jump filter, note lock) ---

    private val standardTwoStrings = listOf(Note("E2", 82.41f), Note("B3", 246.94f))

    @Test
    fun stabilizer_firstFrame_isIgnored_attackPhase() {
        val stabilizer = PitchStabilizer()
        val result = stabilizer.process(82.41f, 0L, standardTwoStrings, emptyList(), 440f)
        assertNull("Prvi frejm treba da postavi baznu vrednost, ne da odmah emituje status", result)
    }

    @Test
    fun stabilizer_withinAttackWindow_isIgnored() {
        val stabilizer = PitchStabilizer()
        stabilizer.process(82.41f, 0L, standardTwoStrings, emptyList(), 440f)
        val result = stabilizer.process(82.41f, TunerConfig.ATTACK_IGNORE_MS - 1, standardTwoStrings, emptyList(), 440f)
        assertNull(result)
    }

    @Test
    fun stabilizer_afterAttackWindow_emitsStatus() {
        val stabilizer = PitchStabilizer()
        stabilizer.process(82.41f, 0L, standardTwoStrings, emptyList(), 440f)
        val result = stabilizer.process(82.41f, TunerConfig.ATTACK_IGNORE_MS + 10, standardTwoStrings, emptyList(), 440f)
        assertNotNull(result)
        assertEquals("E2", result!!.closestNote?.name)
    }

    @Test
    fun stabilizer_suddenJump_isDebounced_thenAccepted() {
        val stabilizer = PitchStabilizer()
        // Uspostavi stabilan pitch na E2
        stabilizer.process(82.41f, 0L, standardTwoStrings, emptyList(), 440f)
        stabilizer.process(82.41f, 150L, standardTwoStrings, emptyList(), 440f)

        // Nagli skok na B3 (npr. korisnik brzo prebacio žicu) - prva dva pokušaja se ignorišu
        val attempt1 = stabilizer.process(246.94f, 300L, standardTwoStrings, emptyList(), 440f)
        val attempt2 = stabilizer.process(246.94f, 450L, standardTwoStrings, emptyList(), 440f)
        assertNull("Prvi ponovljeni skok ne bi trebalo odmah da se prihvati", attempt1)
        assertNull("Drugi ponovljeni skok ne bi trebalo odmah da se prihvati", attempt2)

        // MAX_JUMPS-ti ponovljeni pokušaj se prihvata kao stvarna promena tona
        val attempt3 = stabilizer.process(246.94f, 600L, standardTwoStrings, emptyList(), 440f)
        assertNotNull("Ponovljen skok treba na kraju da se prihvati (promena žice)", attempt3)
        assertEquals("B3", attempt3!!.closestNote?.name)
    }

    @Test
    fun stabilizer_staysLockedOnDecayNoise_doesNotFlicker() {
        // Simulira ono što se dešava dok ton bledi (decay): žica i dalje zvuči, ali malo
        // nestabilnije (šum/civija/harmonik) - zaključana nota ne bi trebalo da "poleti"
        // na drugu žicu zbog par frejmova blagog, nasumičnog odstupanja.
        val stabilizer = PitchStabilizer()
        val targets = listOf(Note("A2", 110.00f), Note("D3", 146.83f))
        stabilizer.process(110f, 0L, targets, emptyList(), 440f)
        stabilizer.process(110f, 150L, targets, emptyList(), 440f)

        val noisySamples = listOf(114f, 105f, 118f, 108f)
        var lastResult: TuningStatus? = null
        var t = 300L
        for (sample in noisySamples) {
            val r = stabilizer.process(sample, t, targets, emptyList(), 440f)
            if (r != null) lastResult = r
            t += 150L
        }

        assertNotNull(lastResult)
        assertEquals("A2", lastResult!!.closestNote?.name)
    }

    @Test
    fun stabilizer_reset_clearsLockedState() {
        val stabilizer = PitchStabilizer()
        stabilizer.process(82.41f, 0L, standardTwoStrings, emptyList(), 440f)
        stabilizer.process(82.41f, 150L, standardTwoStrings, emptyList(), 440f)
        assertTrue(stabilizer.isActive)

        stabilizer.reset()
        assertTrue(!stabilizer.isActive)

        // Posle reseta, ponovo prvi frejm treba da bude ignorisan (nova attack faza)
        val result = stabilizer.process(246.94f, 1000L, standardTwoStrings, emptyList(), 440f)
        assertNull(result)
    }
}
