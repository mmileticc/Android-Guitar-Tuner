package dev.milinko.guitartuner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.milinko.guitartuner.audio.AudioAnalyzer
import dev.milinko.guitartuner.audio.TunerConfig
import dev.milinko.guitartuner.data.TunerPreferencesRepository
import dev.milinko.guitartuner.model.GuitarTunings
import dev.milinko.guitartuner.model.PitchStabilizer
import dev.milinko.guitartuner.model.Tuning
import dev.milinko.guitartuner.model.TuningStatus
import dev.milinko.guitartuner.model.chromaticGridFrequencies
import dev.milinko.guitartuner.model.scaledToReferencePitch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Orkestrira audio capture ([AudioAnalyzer]), pitch-processing pipeline ([PitchStabilizer])
 * i perzistenciju podešavanja ([TunerPreferencesRepository]). Sama obrada pitch signala
 * (octave correction, smoothing, note lock) živi u PitchStabilizer-u - čistoj Kotlin klasi
 * bez Android zavisnosti, testiranoj direktno JVM unit testovima.
 */
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val audioAnalyzer = AudioAnalyzer()
    private val preferences = TunerPreferencesRepository(application)
    private val pitchStabilizer = PitchStabilizer()

    private val _tuningStatus = MutableStateFlow(TuningStatus())
    val tuningStatus: StateFlow<TuningStatus> = _tuningStatus.asStateFlow()

    val volumeFlow: StateFlow<Float> = audioAnalyzer.volumeFlow

    /** Poruka o grešci mikrofona (npr. zauzet drugom aplikacijom), null ako je sve OK. */
    val micError: StateFlow<String?> = audioAnalyzer.micErrorFlow

    private val _selectedBaseTuning = MutableStateFlow(GuitarTunings.ALL_TUNINGS[0])
    private val _referencePitch = MutableStateFlow(TunerConfig.DEFAULT_REFERENCE_PITCH)
    val referencePitch: StateFlow<Float> = _referencePitch.asStateFlow()

    /** Trenutni štim, već skaliran na izabranu referentnu frekvenciju (kalibraciju). */
    val selectedTuning: StateFlow<Tuning> = combine(_selectedBaseTuning, _referencePitch) { tuning, ref ->
        tuning.scaledToReferencePitch(ref)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GuitarTunings.ALL_TUNINGS[0])

    init {
        viewModelScope.launch {
            val saved = preferences.loadInitial()
            _selectedBaseTuning.value = GuitarTunings.ALL_TUNINGS.find { it.name == saved.tuningName }
                ?: GuitarTunings.ALL_TUNINGS[0]
            _referencePitch.value = saved.referencePitch
        }

        viewModelScope.launch {
            audioAnalyzer.pitchFlow.collect { pitch ->
                if (pitch > 0) {
                    val result = pitchStabilizer.process(
                        rawPitch = pitch,
                        currentTimeMs = System.currentTimeMillis(),
                        targetNotes = selectedTuning.value.notes,
                        fallbackTargets = chromaticGridFrequencies(_referencePitch.value),
                        referencePitch = _referencePitch.value
                    )
                    if (result != null) _tuningStatus.value = result
                } else {
                    resetPitchState()
                }
            }
        }
    }

    /** Vraća UI u prazno stanje kad signal utihne (umesto da ostane "zamrznut" na poslednjoj noti). */
    private fun resetPitchState() {
        if (!pitchStabilizer.isActive && _tuningStatus.value.frequency == 0f) return
        pitchStabilizer.reset()
        _tuningStatus.value = TuningStatus()
    }

    fun changeTuning(tuning: Tuning) {
        _selectedBaseTuning.value = tuning
        pitchStabilizer.reset()
        _tuningStatus.value = TuningStatus()
        viewModelScope.launch { preferences.saveTuningName(tuning.name) }
    }

    /** Kalibracija referentne frekvencije A4 (438-445Hz). */
    fun setReferencePitch(hz: Float) {
        val clamped = hz.coerceIn(TunerConfig.REFERENCE_PITCH_MIN, TunerConfig.REFERENCE_PITCH_MAX)
        _referencePitch.value = clamped
        pitchStabilizer.reset()
        viewModelScope.launch { preferences.saveReferencePitch(clamped) }
    }

    fun startListening() = audioAnalyzer.startListening()
    fun stopListening() = audioAnalyzer.stopListening()
}
