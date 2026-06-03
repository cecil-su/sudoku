package com.sudoku.game.model

import org.junit.Assert.*
import org.junit.Test

class SessionTelemetryTest {

    @Test
    fun `starts empty`() {
        val t = SessionTelemetry()
        assertEquals(0, t.hintsRequested)
        assertEquals(0, t.errorsMade)
        assertEquals(0, t.demosViewed)
        assertEquals(0, t.challengesAttempted)
        assertEquals(0, t.challengesCorrect)
        assertTrue(t.techniqueHelpCounts.isEmpty())
    }

    @Test
    fun `records hints and accumulates stuck techniques`() {
        val t = SessionTelemetry()
            .recordHint("区块排除")
            .recordHint("区块排除")
            .recordHint(null)
        assertEquals(3, t.hintsRequested)
        assertEquals(2, t.techniqueHelpCounts["区块排除"])
        assertEquals("null technique adds no key", 1, t.techniqueHelpCounts.size)
    }

    @Test
    fun `correct challenge counts attempt but not stuck technique`() {
        val t = SessionTelemetry().recordChallenge(correct = true, technique = "X-Wing")
        assertEquals(1, t.challengesAttempted)
        assertEquals(1, t.challengesCorrect)
        assertTrue(t.techniqueHelpCounts.isEmpty())
    }

    @Test
    fun `wrong challenge counts as needing help on that technique`() {
        val t = SessionTelemetry().recordChallenge(correct = false, technique = "X-Wing")
        assertEquals(1, t.challengesAttempted)
        assertEquals(0, t.challengesCorrect)
        assertEquals(1, t.techniqueHelpCounts["X-Wing"])
    }

    @Test
    fun `errors and demos are independent counters`() {
        val t = SessionTelemetry().recordError().recordError().recordDemoViewed()
        assertEquals(2, t.errorsMade)
        assertEquals(1, t.demosViewed)
    }
}
