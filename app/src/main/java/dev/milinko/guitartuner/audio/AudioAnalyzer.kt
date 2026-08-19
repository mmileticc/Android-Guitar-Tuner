package dev.milinko.guitartuner.audio

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

class AudioAnalyzer {
    private var dispatcher: AudioDispatcher? = null
    val pitchFlow = MutableStateFlow(DEFAULT_PITCH)
    val volumeFlow = MutableStateFlow(0f)

    private var smoothedPitch = 0f
    private var smoothedVolume = 0f

    // NOVE VARIJABLE ZA STABILNOST
    private var invalidDetectionCount = 0

    fun startListening() {
        if (dispatcher != null) return // Zaštita da ne pokreneš dva puta

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP)

        // 1. Prvo izvlačimo jačinu
        val volumeProcessor = object : AudioProcessor {
            override fun process(audioEvent: be.tarsos.dsp.AudioEvent?): Boolean {
                audioEvent?.let {
                    val rms = it.rms.toFloat()
                    smoothedVolume = smoothedVolume * VOLUME_SMOOTHING + rms * (1f - VOLUME_SMOOTHING) // EMA
                    volumeFlow.value = smoothedVolume
                }
                return true
            }
            override fun processingFinished() {}
        }

        // 2. Onda detekcija tona
        val pdh = PitchDetectionHandler { result, _ ->
            val pitch = result.pitch
            val prob = result.probability

            // LOGIKA POVERENJA:
            if (pitch > PITCH_MIN_HZ && pitch < PITCH_MAX_HZ && prob > MIN_PROBABILITY && smoothedVolume > MIN_VOLUME_THRESHOLD) {
                // Signal je čist i jak
                invalidDetectionCount = 0

                // EMA filter za pitch (0.2f je dobro)
                smoothedPitch = smoothedPitch * PITCH_SMOOTHING + pitch * (1f - PITCH_SMOOTHING)
                pitchFlow.value = smoothedPitch
            } else {
                // Algoritam trenutno ne vidi dobar ton, ali ne gasimo odmah!
                invalidDetectionCount++

                if (invalidDetectionCount > MAX_INVALID_ATTEMPTS) {
                    fadeOut()
                }
            }
        }

        val pitchProcessor = PitchProcessor(
            PitchProcessor.PitchEstimationAlgorithm.YIN,
            SAMPLE_RATE.toFloat(),
            BUFFER_SIZE,
            pdh
        )

        dispatcher?.addAudioProcessor(volumeProcessor)
        dispatcher?.addAudioProcessor(pitchProcessor)

        Thread(dispatcher, "Audio Analyzer Thread").start()
    }

    private fun fadeOut() {
        if (smoothedPitch > 0) {
            smoothedPitch *= FADE_OUT_FACTOR // Lagani pad
            if (smoothedPitch < FADE_OUT_THRESHOLD) smoothedPitch = DEFAULT_PITCH
            pitchFlow.value = smoothedPitch
        }
    }

    fun stopListening() {
        dispatcher?.stop()
        dispatcher = null
        smoothedPitch = DEFAULT_PITCH
        pitchFlow.value = DEFAULT_PITCH
    }

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val BUFFER_SIZE = 1024
        private const val OVERLAP = 512
        
        private const val MAX_INVALID_ATTEMPTS = 5
        
        private const val VOLUME_SMOOTHING = 0.8f
        private const val PITCH_SMOOTHING = 0.8f
        
        private const val PITCH_MIN_HZ = 60f
        private const val PITCH_MAX_HZ = 500f
        private const val MIN_PROBABILITY = 0.80f
        private const val MIN_VOLUME_THRESHOLD = 0.008f
        
        private const val FADE_OUT_FACTOR = 0.85f
        private const val FADE_OUT_THRESHOLD = 40f
        
        private const val DEFAULT_PITCH = -1f
    }
}
