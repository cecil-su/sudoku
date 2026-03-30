package com.sudoku.game.model

enum class Difficulty(
    val label: String,
    val maxTechniqueLevel: Int,
    val targetRemovals: IntRange
) {
    BEGINNER("入门", 2, 30..38),   // Naked Single + Hidden Single
    EASY("简单", 3, 35..45),       // + Pointing/Claiming
    MEDIUM("中等", 4, 40..50),     // + Naked Pair/Triple
    HARD("困难", 5, 45..55),       // + Hidden Pair/Triple
    EXPERT("专家", 6, 48..62);     // + X-Wing
}
