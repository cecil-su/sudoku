package com.sudoku.game.model

data class GameState(
    val cells: List<List<Cell>>,
    val solution: List<List<Int>>,
    val difficulty: Difficulty,
    val isCompleted: Boolean = false,
    val selectedRow: Int = -1,
    val selectedCol: Int = -1,
    val isNoteMode: Boolean = false,
    val hintsUsed: Int = 0,
    val errorCount: Int = 0,
    val restoredElapsedSeconds: Long = 0
) {
    companion object {
        const val SIZE = 9
        const val BOX_SIZE = 3
        const val MAX_HINTS = 3
    }

    val hasSelection: Boolean get() = selectedRow in 0..8 && selectedCol in 0..8

    fun getCell(row: Int, col: Int): Cell = cells[row][col]

    fun isSelected(row: Int, col: Int): Boolean = row == selectedRow && col == selectedCol

    fun isSameRowColBox(row: Int, col: Int): Boolean {
        if (!hasSelection) return false
        return row == selectedRow || col == selectedCol ||
            (row / BOX_SIZE == selectedRow / BOX_SIZE && col / BOX_SIZE == selectedCol / BOX_SIZE)
    }

    fun isSameValue(row: Int, col: Int): Boolean {
        if (!hasSelection) return false
        val selectedValue = cells[selectedRow][selectedCol].value
        return selectedValue != 0 && cells[row][col].value == selectedValue
    }
}
