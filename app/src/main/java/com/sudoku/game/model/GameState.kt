package com.sudoku.game.model

data class GameState(
    val cells: List<List<Cell>>,
    val solution: List<List<Int>>,
    val difficulty: Difficulty,
    val isCompleted: Boolean = false,
    val selectedCell: Pair<Int, Int>? = null,
    val isNoteMode: Boolean = false,
    val elapsedSeconds: Long = 0,
    val hintsUsed: Int = 0,
    val errorCount: Int = 0
) {
    companion object {
        const val SIZE = 9
        const val BOX_SIZE = 3
        const val MAX_HINTS = 3
    }

    fun getCell(row: Int, col: Int): Cell = cells[row][col]

    fun isSelected(row: Int, col: Int): Boolean = selectedCell == Pair(row, col)

    fun isSameRowColBox(row: Int, col: Int): Boolean {
        val (sr, sc) = selectedCell ?: return false
        return row == sr || col == sc || (row / BOX_SIZE == sr / BOX_SIZE && col / BOX_SIZE == sc / BOX_SIZE)
    }

    fun isSameValue(row: Int, col: Int): Boolean {
        val (sr, sc) = selectedCell ?: return false
        val selectedValue = cells[sr][sc].value
        return selectedValue != 0 && cells[row][col].value == selectedValue
    }
}
