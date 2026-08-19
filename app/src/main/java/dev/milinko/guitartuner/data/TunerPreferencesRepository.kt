package dev.milinko.guitartuner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.milinko.guitartuner.audio.TunerConfig
import dev.milinko.guitartuner.model.GuitarTunings
import kotlinx.coroutines.flow.first

private val Context.tunerDataStore by preferencesDataStore(name = "tuner_prefs")

data class SavedTunerPrefs(
    val tuningName: String,
    val referencePitch: Float
)

/**
 * Čuva poslednji izabrani štim i kalibraciju (referentnu frekvenciju A4) preko
 * Jetpack DataStore, tako da se ne resetuju na svaki restart aplikacije.
 */
class TunerPreferencesRepository(private val context: Context) {

    private val keyTuningName = stringPreferencesKey("selected_tuning_name")
    private val keyReferencePitch = floatPreferencesKey("reference_pitch")

    suspend fun loadInitial(): SavedTunerPrefs {
        val prefs = context.tunerDataStore.data.first()
        val tuningName = prefs[keyTuningName] ?: GuitarTunings.ALL_TUNINGS[0].name
        val referencePitch = prefs[keyReferencePitch] ?: TunerConfig.DEFAULT_REFERENCE_PITCH
        return SavedTunerPrefs(tuningName, referencePitch)
    }

    suspend fun saveTuningName(name: String) {
        context.tunerDataStore.edit { it[keyTuningName] = name }
    }

    suspend fun saveReferencePitch(hz: Float) {
        context.tunerDataStore.edit { it[keyReferencePitch] = hz }
    }
}
