package com.sudoku.game.model

/**
 * User-facing game preferences (separate from AI provider config in [AiSettings]).
 * Plain immutable holder; persisted field-by-field by `GameSettingsRepository`.
 */
data class GameSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val errorCheck: ErrorCheckMode = ErrorCheckMode.IMMEDIATE,
    val soundEnabled: Boolean = true,
    val autoRemoveNotes: Boolean = true,
    val showTimer: Boolean = true
)

/** App theme: follow system light/dark, force light/dark, or the warm eye-care palette. */
enum class ThemeChoice(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("亮色"),
    DARK("暗色"),
    WARM("暖色护眼");

    companion object {
        /** Tolerant lookup for persisted/corrupt values — unknown/null falls back to [SYSTEM]. */
        fun fromName(name: String?): ThemeChoice = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** When wrong entries get flagged: live as you type, only on demand, or never. */
enum class ErrorCheckMode(val label: String) {
    IMMEDIATE("即时检查"),
    MANUAL("手动检查"),
    OFF("不检查");

    companion object {
        /** Tolerant lookup for persisted/corrupt values — unknown/null falls back to [IMMEDIATE]. */
        fun fromName(name: String?): ErrorCheckMode = entries.firstOrNull { it.name == name } ?: IMMEDIATE
    }
}
