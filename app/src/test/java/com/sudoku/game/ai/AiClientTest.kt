package com.sudoku.game.ai

import org.junit.Assert.*
import org.junit.Test

/** Covers the pure [isSecureUrl] guard — the chokepoint that keeps the BYOK key
 *  off any non-https endpoint (S1). The IO/org.json parts of AiClient bind to the
 *  Android framework and aren't JVM-unit-testable, matching the project's pattern
 *  of testing extracted pure logic only. */
class AiClientTest {

    @Test fun `https is accepted`() =
        assertTrue(isSecureUrl("https://api.deepseek.com"))

    @Test fun `scheme check is case-insensitive`() =
        assertTrue(isSecureUrl("HTTPS://api.openai.com/v1"))

    @Test fun `leading whitespace is tolerated`() =
        assertTrue(isSecureUrl("  https://api.deepseek.com  "))

    @Test fun `plain http is rejected`() =
        assertFalse(isSecureUrl("http://api.deepseek.com"))

    @Test fun `other schemes are rejected`() {
        assertFalse(isSecureUrl("ftp://example.com"))
        assertFalse(isSecureUrl("api.deepseek.com"))
        assertFalse(isSecureUrl(""))
    }
}
