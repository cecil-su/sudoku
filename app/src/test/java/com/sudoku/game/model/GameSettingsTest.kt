package com.sudoku.game.model

import org.junit.Assert.*
import org.junit.Test

/** Covers the tolerant enum lookups used when decoding persisted settings — corrupt
 *  or missing values must fall back to a safe default instead of throwing (the
 *  DataStore decode itself binds to Android and isn't JVM-unit-testable). */
class GameSettingsTest {

    @Test fun `defaults are sensible`() {
        val s = GameSettings()
        assertEquals(ThemeChoice.SYSTEM, s.theme)
        assertEquals(ErrorCheckMode.IMMEDIATE, s.errorCheck)
        assertTrue(s.soundEnabled)
        assertTrue(s.autoRemoveNotes)
        assertTrue(s.showTimer)
    }

    @Test fun `ThemeChoice fromName round-trips a valid name`() =
        assertEquals(ThemeChoice.WARM, ThemeChoice.fromName("WARM"))

    @Test fun `ThemeChoice fromName falls back on null or garbage`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromName(null))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromName("warm"))   // case-sensitive on purpose
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromName("nope"))
    }

    @Test fun `ErrorCheckMode fromName round-trips and falls back`() {
        assertEquals(ErrorCheckMode.MANUAL, ErrorCheckMode.fromName("MANUAL"))
        assertEquals(ErrorCheckMode.IMMEDIATE, ErrorCheckMode.fromName(null))
        assertEquals(ErrorCheckMode.IMMEDIATE, ErrorCheckMode.fromName("???"))
    }
}
