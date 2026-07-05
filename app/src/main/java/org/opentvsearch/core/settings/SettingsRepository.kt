package org.opentvsearch.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opentvsearch_settings")

/**
 * Minimal settings store. The headline preference is [voiceOnLaunch]: when true,
 * SearchActivity fires the voice recognizer immediately on launch (the requested
 * toggle for voice-triggered-on-app-launch).
 */
class SettingsRepository(private val context: Context) {

    val voiceOnLaunch: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_VOICE_ON_LAUNCH] ?: false }

    suspend fun setVoiceOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VOICE_ON_LAUNCH] = enabled }
    }

    companion object {
        private val KEY_VOICE_ON_LAUNCH = booleanPreferencesKey("voice_on_launch")
    }
}
