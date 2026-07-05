package org.opentvsearch.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opentvsearch_settings")

/**
 * Settings store. Beyond the headline [voiceOnLaunch] pref (when true SearchActivity fires the
 * voice recognizer immediately on launch), M2 adds configurable SEARCH SOURCES:
 *
 *  - [disabledSourceIds]: the set of source ids the user has TURNED OFF. We persist DISABLED ids
 *    (not enabled ones) so any newly-appeared source defaults to enabled without a migration.
 *  - [sourceOrder]: an ordered list of source ids expressing the user's preferred source order.
 *    Empty = use the natural/recommended order. Ids absent from this list keep their natural
 *    position AFTER the ordered ones (see core/sources/SourceCatalog.applySourceOrder).
 *
 * Source ids match the LIVE source ids so the AppModule filter keys off the exact same strings:
 *   - "tvprovider" for TvProviderSearchSource,
 *   - "handoff:<packageName>" for each DeepLinkHandoffSource.
 */
class SettingsRepository(private val context: Context) {

    val voiceOnLaunch: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_VOICE_ON_LAUNCH] ?: false }

    /** Ids the user disabled. Default empty = every source enabled. */
    val disabledSourceIds: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_DISABLED_SOURCE_IDS] ?: emptySet() }

    /** User's preferred source order (list of ids). Default empty = natural/recommended order. */
    val sourceOrder: Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_SOURCE_ORDER]
                ?.split(ORDER_DELIMITER)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    suspend fun setVoiceOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VOICE_ON_LAUNCH] = enabled }
    }

    /** Enable/disable a single source by id. Stores DISABLED ids so new sources default on. */
    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_SOURCE_IDS] ?: emptySet()
            prefs[KEY_DISABLED_SOURCE_IDS] =
                if (enabled) current - id else current + id
        }
    }

    /** Persist the full ordered id list (empty clears the custom order). */
    suspend fun setSourceOrder(orderedIds: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOURCE_ORDER] = orderedIds.joinToString(ORDER_DELIMITER)
        }
    }

    companion object {
        private val KEY_VOICE_ON_LAUNCH = booleanPreferencesKey("voice_on_launch")
        private val KEY_DISABLED_SOURCE_IDS = stringSetPreferencesKey("disabled_source_ids")
        private val KEY_SOURCE_ORDER = stringPreferencesKey("source_order")

        /** Source ids never contain a newline, so a newline is a safe list delimiter. */
        private const val ORDER_DELIMITER = "\n"
    }
}
