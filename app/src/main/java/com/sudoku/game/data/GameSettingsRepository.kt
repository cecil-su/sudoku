package com.sudoku.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sudoku.game.model.ErrorCheckMode
import com.sudoku.game.model.GameSettings
import com.sudoku.game.model.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "game_settings")

/**
 * Persists [GameSettings] in a Preferences DataStore (one key per field), following
 * the [StatsRepository] pattern. Enum fields decode through their tolerant
 * `fromName` so a corrupt/old value degrades to the default instead of crashing.
 */
class GameSettingsRepository(private val context: Context) {

    val settings: Flow<GameSettings> = context.gameSettingsDataStore.data.map { prefs ->
        GameSettings(
            theme = ThemeChoice.fromName(prefs[THEME]),
            errorCheck = ErrorCheckMode.fromName(prefs[ERROR_CHECK]),
            soundEnabled = prefs[SOUND] ?: true,
            autoRemoveNotes = prefs[AUTO_NOTES] ?: true,
            showTimer = prefs[SHOW_TIMER] ?: true
        )
    }

    suspend fun setTheme(theme: ThemeChoice) = edit { it[THEME] = theme.name }
    suspend fun setErrorCheck(mode: ErrorCheckMode) = edit { it[ERROR_CHECK] = mode.name }
    suspend fun setSoundEnabled(enabled: Boolean) = edit { it[SOUND] = enabled }
    suspend fun setAutoRemoveNotes(enabled: Boolean) = edit { it[AUTO_NOTES] = enabled }
    suspend fun setShowTimer(enabled: Boolean) = edit { it[SHOW_TIMER] = enabled }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.gameSettingsDataStore.edit(block)
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ERROR_CHECK = stringPreferencesKey("error_check")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val AUTO_NOTES = booleanPreferencesKey("auto_remove_notes")
        val SHOW_TIMER = booleanPreferencesKey("show_timer")
    }
}
