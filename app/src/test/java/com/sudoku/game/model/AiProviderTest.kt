package com.sudoku.game.model

import org.junit.Assert.*
import org.junit.Test

/** Covers [AiProvider.isUsable] — gates the coach UI so a provider with no model
 *  to call doesn't appear available (S2). */
class AiProviderTest {

    private fun provider(models: List<String> = emptyList(), active: String? = null) =
        AiProvider(id = "1", name = "p", baseUrl = "https://x", apiKey = "k", models = models, activeModel = active)

    @Test fun `no model is not usable`() =
        assertFalse(provider().isUsable)

    @Test fun `an active model makes it usable`() =
        assertTrue(provider(active = "deepseek-chat").isUsable)

    @Test fun `a non-empty model list makes it usable`() =
        assertTrue(provider(models = listOf("gpt-4o")).isUsable)

    @Test fun `both present is usable`() =
        assertTrue(provider(models = listOf("gpt-4o"), active = "gpt-4o").isUsable)
}
