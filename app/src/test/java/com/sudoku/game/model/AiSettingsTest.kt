package com.sudoku.game.model

import org.junit.Assert.*
import org.junit.Test

class AiSettingsTest {

    private fun provider(id: String, name: String = id) =
        AiProvider(id = id, name = name, baseUrl = "https://x", apiKey = "k")

    @Test
    fun `starts empty with no active provider`() {
        val s = AiSettings()
        assertTrue(s.providers.isEmpty())
        assertNull(s.activeProviderId)
        assertNull(s.activeProvider)
    }

    @Test
    fun `first upsert becomes active automatically`() {
        val s = AiSettings().upsert(provider("a"))
        assertEquals(1, s.providers.size)
        assertEquals("a", s.activeProviderId)
        assertEquals("a", s.activeProvider?.id)
    }

    @Test
    fun `second upsert does not steal active`() {
        val s = AiSettings().upsert(provider("a")).upsert(provider("b"))
        assertEquals(2, s.providers.size)
        assertEquals("a", s.activeProviderId)
    }

    @Test
    fun `upsert with existing id replaces in place`() {
        val s = AiSettings().upsert(provider("a", "A")).upsert(provider("a", "A2"))
        assertEquals(1, s.providers.size)
        assertEquals("A2", s.activeProvider?.name)
    }

    @Test
    fun `remove active falls back to first remaining`() {
        val s = AiSettings().upsert(provider("a")).upsert(provider("b")).remove("a")
        assertEquals(1, s.providers.size)
        assertEquals("b", s.activeProviderId)
    }

    @Test
    fun `remove last clears active`() {
        val s = AiSettings().upsert(provider("a")).remove("a")
        assertTrue(s.providers.isEmpty())
        assertNull(s.activeProviderId)
    }

    @Test
    fun `setActive switches and ignores unknown id`() {
        val s = AiSettings().upsert(provider("a")).upsert(provider("b"))
        assertEquals("b", s.setActive("b").activeProviderId)
        assertEquals("a", s.setActive("zzz").activeProviderId)
    }

    @Test
    fun `setModel updates only the target provider`() {
        val s = AiSettings().upsert(provider("a")).upsert(provider("b"))
            .setModel("a", "deepseek-chat")
        assertEquals("deepseek-chat", s.providers.first { it.id == "a" }.activeModel)
        assertNull(s.providers.first { it.id == "b" }.activeModel)
    }
}
