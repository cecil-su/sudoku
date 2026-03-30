package com.sudoku.game.model

data class GameStats(
    val totalCompleted: Map<Difficulty, Int> = Difficulty.entries.associateWith { 0 },
    val bestTimes: Map<Difficulty, Long> = Difficulty.entries.associateWith { 0L },
    val totalStarted: Int = 0
) {
    val totalCompletedCount: Int get() = totalCompleted.values.sum()

    fun bestTimeFormatted(difficulty: Difficulty): String {
        val seconds = bestTimes[difficulty] ?: 0L
        if (seconds == 0L) return "--:--"
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
