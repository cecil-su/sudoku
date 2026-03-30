package com.sudoku.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sudoku.game.model.Difficulty
import com.sudoku.game.model.GameStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "game_stats")

class StatsRepository(private val context: Context) {

    fun getStats(): Flow<GameStats> {
        return context.dataStore.data.map { prefs ->
            GameStats(
                totalCompleted = Difficulty.entries.associateWith { diff ->
                    prefs[completedKey(diff)] ?: 0
                },
                bestTimes = Difficulty.entries.associateWith { diff ->
                    prefs[bestTimeKey(diff)] ?: 0L
                },
                totalStarted = prefs[TOTAL_STARTED] ?: 0
            )
        }
    }

    suspend fun recordGameStarted() {
        context.dataStore.edit { prefs ->
            prefs[TOTAL_STARTED] = (prefs[TOTAL_STARTED] ?: 0) + 1
        }
    }

    suspend fun recordGameCompleted(difficulty: Difficulty, timeSeconds: Long) {
        context.dataStore.edit { prefs ->
            // Increment completed count
            val key = completedKey(difficulty)
            prefs[key] = (prefs[key] ?: 0) + 1

            // Update best time
            val bestKey = bestTimeKey(difficulty)
            val currentBest = prefs[bestKey] ?: 0L
            if (currentBest == 0L || timeSeconds < currentBest) {
                prefs[bestKey] = timeSeconds
            }
        }
    }

    private fun completedKey(diff: Difficulty) = intPreferencesKey("completed_${diff.name}")
    private fun bestTimeKey(diff: Difficulty) = longPreferencesKey("best_time_${diff.name}")

    companion object {
        private val TOTAL_STARTED = intPreferencesKey("total_started")
    }
}
