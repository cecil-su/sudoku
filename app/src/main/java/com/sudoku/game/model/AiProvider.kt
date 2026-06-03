package com.sudoku.game.model

/**
 * One AI provider configuration (BYOK — bring your own key).
 *
 * [baseUrl] is an OpenAI-compatible base, e.g. `https://api.deepseek.com`.
 * [apiKey] is stored in plaintext (single-user personal app). [models] is the
 * known model list (preset + manually added); [activeModel] is the one selected
 * for actual use.
 */
data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<String> = emptyList(),
    val activeModel: String? = null
)
