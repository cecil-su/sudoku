package com.sudoku.game.model

enum class Difficulty(
    val label: String,
    val cluesToRemove: IntRange
) {
    BEGINNER("入门", 30..35),
    EASY("简单", 36..42),
    MEDIUM("中等", 43..50),
    HARD("困难", 50..55),
    EXPERT("专家", 55..62);
}
