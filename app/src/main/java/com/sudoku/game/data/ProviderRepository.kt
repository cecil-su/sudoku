package com.sudoku.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sudoku.game.model.AiProvider
import com.sudoku.game.model.AiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * Persists [AiSettings] as a single JSON string in a Preferences DataStore — same
 * org.json approach as [GameRepository]. All mutations read-modify-write through
 * the pure transitions on [AiSettings]; corrupt JSON degrades to empty settings.
 */
class ProviderRepository(private val context: Context) {

    val settings: Flow<AiSettings> = context.aiDataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { decode(it) }.getOrNull() } ?: AiSettings()
    }

    suspend fun upsertProvider(provider: AiProvider) = update { it.upsert(provider) }
    suspend fun removeProvider(id: String) = update { it.remove(id) }
    suspend fun setActive(id: String) = update { it.setActive(id) }
    suspend fun setModel(providerId: String, model: String) = update { it.setModel(providerId, model) }

    private suspend fun update(transform: (AiSettings) -> AiSettings) {
        context.aiDataStore.edit { prefs ->
            val current = prefs[KEY]?.let { runCatching { decode(it) }.getOrNull() } ?: AiSettings()
            prefs[KEY] = encode(transform(current))
        }
    }

    private fun encode(s: AiSettings): String = JSONObject().apply {
        put("activeProviderId", s.activeProviderId ?: JSONObject.NULL)
        put("providers", JSONArray().apply {
            for (p in s.providers) put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                put("apiKey", p.apiKey)
                put("models", JSONArray(p.models))
                put("activeModel", p.activeModel ?: JSONObject.NULL)
            })
        })
    }.toString()

    private fun decode(text: String): AiSettings {
        val obj = JSONObject(text)
        val arr = obj.getJSONArray("providers")
        val providers = (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val modelsArr = p.getJSONArray("models")
            AiProvider(
                id = p.getString("id"),
                name = p.getString("name"),
                baseUrl = p.getString("baseUrl"),
                apiKey = p.getString("apiKey"),
                models = (0 until modelsArr.length()).map { modelsArr.getString(it) },
                activeModel = if (p.isNull("activeModel")) null else p.getString("activeModel")
            )
        }
        return AiSettings(
            providers = providers,
            activeProviderId = if (obj.isNull("activeProviderId")) null else obj.getString("activeProviderId")
        )
    }

    private companion object {
        val KEY = stringPreferencesKey("ai_settings_json")
    }
}
