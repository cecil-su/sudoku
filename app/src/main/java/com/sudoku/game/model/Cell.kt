package com.sudoku.game.model

data class Cell(
    val row: Int,
    val col: Int,
    val value: Int = 0,
    val isGiven: Boolean = false,
    val notes: Set<Int> = emptySet(),
    val isError: Boolean = false
) {
    val isEmpty: Boolean get() = value == 0
    val box: Int get() = (row / 3) * 3 + col / 3
}
