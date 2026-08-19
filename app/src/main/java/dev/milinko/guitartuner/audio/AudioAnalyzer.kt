package dev.milinko.guitartuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.android.AndroidAudioInputStream
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Audio capture + pitch detection (YIN preko TarsosDSP).
 *
 * Namerno emituje SIROV (ne EMA-zaglađen) pitch po frejmu - sve zaglađivanje/histerezis
 * radi TunerViewModel (jedan, koherentan pipeline). Ranije je ova klasa imala svoj EMA
 * smoothing, pa je ViewModel dodatno zaglađivao već zaglađenu vrednost - dupli filter je
 * pravio "spor" osećaj igle. Sad AudioAnalyzer samo gate-uje (probability/volume/range)
 * i prosleđuje sirovu detekciju.
 */
class AudioAnalyzer {
    private var dispatcher: AudioDispatcher? = null
    private var manualAudioRecord: AudioRecord? = null

    val pitchFlow = MutableStateFlow(DEFAULT_PITCH)
    val volumeFlow = MutableStateFlow(0f)

    /** Poruka o grešci mikrofona (npr. zauzet drugom aplikacijom) ili null ako je sve OK. */
    val micErrorFlow = MutableStateFlow<String?>(null)

    private var smoothedVolume = 0f
    private var lastValidDetectionTimeMs = 0L

    fun startListening() {
        if (dispatcher != null) return // Zaštita da ne pokreneš dva puta

        micErrorFlow.value = null

        dispatcher = try {
            createVoiceRecognitionDispatcher()
        } catch (e: Exception) {
            // Fallback na podrazumevani mikrofonski izvor ako VOICE_RECOGNITION nije dostupan
            // na ovom uređaju (retko, ali postoje jeftiniji uređaji bez tog audio source-a).
            try {
                AudioDispatcherFactory.fromDefaultMicrophone(
                    TunerConfig.SAMPLE_RATE,
                    TunerConfig.BUFFER_SIZE,
                    TunerConfig.OVERLAP
                )
            } catch (fallbackError: Exception) {
                micErrorFlow.value = "Mikrofon nije dostupan (možda ga koristi druga aplikacija)."
                null
            }
        }

        val activeDispatcher = dispatcher ?: return

        // 1. Jačina signala (RMS) - koristi se samo za vizuelni pulsirajući krug
        val volumeProcessor = object : AudioProcessor {
            override fun process(audioEvent: be.tarsos.dsp.AudioEvent?): Boolean {
                audioEvent?.let {
                    val rms = it.rms.toFloat()
                    smoothedVolume = smoothedVolume * TunerConfig.VOLUME_SMOOTHING + rms * (1f - TunerConfig.VOLUME_SMOOTHING)
                    volumeFlow.value = smoothedVolume
                }
                return true
            }
            override fun processingFinished() {}
        }

        // 2. Detekcija tona (YIN)
        val pdh = PitchDetectionHandler { result, _ ->
            val pitch = result.pitch
            val prob = result.probability

            // HISTEREZIS na jačinu signala: dok VEĆ pratimo aktivnu notu (pitchFlow > 0),
            // dovoljan je slabiji signal da se nastavi praćenje kroz prirodno bledenje (decay)
            // žice. Za NOVU notu treba jači signal - izbegava lažne okidače od šuma/civije.
            val isCurrentlyActive = pitchFlow.value > 0
            val volumeThreshold = if (isCurrentlyActive) {
                TunerConfig.MIN_VOLUME_THRESHOLD_HOLD
            } else {
                TunerConfig.MIN_VOLUME_THRESHOLD_ENTER
            }

            val isValid = pitch > TunerConfig.PITCH_MIN_HZ && pitch < TunerConfig.PITCH_MAX_HZ &&
                prob > TunerConfig.MIN_PROBABILITY && smoothedVolume > volumeThreshold

            if (isValid) {
                lastValidDetectionTimeMs = System.currentTimeMillis()
                pitchFlow.value = pitch // SIROVA vrednost - ViewModel radi octave-correction + smoothing
            } else {
                // NE diramo pitchFlow ovde - igla se DRŽI na poslednjoj dobroj vrednosti dok
                // signal stvarno ne utihne (SILENCE_HOLD_MS). Ranije je ovde postojao veštački
                // "fade out" niz opadajućih Hz vrednosti koji se mešao sa povremenim pravim
                // detekcijama tokom bledenja tona i pravio haos u igli (skakanje levo-desno).
                val now = System.currentTimeMillis()
                if (lastValidDetectionTimeMs != 0L && now - lastValidDetectionTimeMs > TunerConfig.SILENCE_HOLD_MS) {
                    if (pitchFlow.value != DEFAULT_PITCH) {
                        pitchFlow.value = DEFAULT_PITCH
                    }
                }
            }
        }

        val pitchProcessor = PitchProcessor(
            PitchProcessor.PitchEstimationAlgorithm.YIN,
            TunerConfig.SAMPLE_RATE.toFloat(),
            TunerConfig.BUFFER_SIZE,
            pdh
        )

        activeDispatcher.addAudioProcessor(volumeProcessor)
        activeDispatcher.addAudioProcessor(pitchProcessor)

        Thread(activeDispatcher, "Audio Analyzer Thread").start()
    }

    /**
     * Ručno pravi AudioRecord sa MediaRecorder.AudioSource.VOICE_RECOGNITION umesto
     * default mikrofonskog izvora. VOICE_RECOGNITION eksplicitno traži od Android audio
     * stack-a da isključi AGC (automatic gain control), noise suppression i echo cancellation -
     * ti efekti su glavni izvor izobličenja signala kod pitch detekcije na default MIC izvoru.
     */
    @SuppressLint("MissingPermission") // RECORD_AUDIO se proverava pre poziva startListening() (vidi MainActivity/PermissionGate)
    private fun createVoiceRecognitionDispatcher(): AudioDispatcher {
        val minBufferSize = AudioRecord.getMinBufferSize(
            TunerConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferSize > 0) { "AudioRecord.getMinBufferSize failed" }

        val recordBufferSize = maxOf(minBufferSize, TunerConfig.BUFFER_SIZE * 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            TunerConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            error("AudioRecord failed to initialize with VOICE_RECOGNITION source")
        }

        manualAudioRecord = audioRecord

        val tarsosFormat = TarsosDSPAudioFormat(TunerConfig.SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val inputStream = AndroidAudioInputStream(audioRecord, tarsosFormat)

        audioRecord.startRecording()

        return AudioDispatcher(inputStream, TunerConfig.BUFFER_SIZE, TunerConfig.OVERLAP)
    }

    fun stopListening() {
        dispatcher?.stop()
        dispatcher = null
        manualAudioRecord?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        manualAudioRecord = null
        lastValidDetectionTimeMs = 0L
        pitchFlow.value = DEFAULT_PITCH
    }

    companion object {
        private const val DEFAULT_PITCH = -1f
    }
}
