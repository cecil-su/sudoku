package com.sudoku.game.model

/**
 * Per-game learning telemetry. Held only in the ViewModel (the same isolation as
 * [Hint]'s `_activeHint`): it never enters [GameState] serialization and is never
 * sent anywhere — no AI request carries it. Collection starts now (10.2) so the
 * weak-spot picture isn't empty later; 10.4 will add AI-usage events and 10.5 the
 * review UI that reads it. For now we only record, never display.
 *
 * Captures help asked (hints), mistakes, demos watched, "your turn" challenge
 * outcomes, and which techniques the player keeps needing help on
 * ([techniqueHelpCounts] = the stuck-technique picture). Region-dwell timing is a
 * later refinement.
 */
data class SessionTelemetry(
    val hintsRequested: Int = 0,
    val errorsMade: Int = 0,
    val demosViewed: Int = 0,
    val challengesAttempted: Int = 0,
    val challengesCorrect: Int = 0,
    val aiInteractions: Int = 0,
    val techniqueHelpCounts: Map<String, Int> = emptyMap()
) {
    fun recordHint(technique: String?): SessionTelemetry = copy(
        hintsRequested = hintsRequested + 1,
        techniqueHelpCounts = techniqueHelpCounts.bump(technique)
    )

    fun recordError(): SessionTelemetry = copy(errorsMade = errorsMade + 1)

    fun recordDemoViewed(): SessionTelemetry = copy(demosViewed = demosViewed + 1)

    /** An AI coach turn (text or voice) — local-only, like every other counter. */
    fun recordAiUsed(): SessionTelemetry = copy(aiInteractions = aiInteractions + 1)

    /** A wrong answer also counts as needing help on that technique. */
    fun recordChallenge(correct: Boolean, technique: String?): SessionTelemetry = copy(
        challengesAttempted = challengesAttempted + 1,
        challengesCorrect = challengesCorrect + if (correct) 1 else 0,
        techniqueHelpCounts = if (correct) techniqueHelpCounts else techniqueHelpCounts.bump(technique)
    )

    private fun Map<String, Int>.bump(key: String?): Map<String, Int> =
        if (key == null) this else this + (key to (getOrDefault(key, 0) + 1))
}
