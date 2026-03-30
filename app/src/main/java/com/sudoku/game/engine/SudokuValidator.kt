package com.sudoku.game.engine

import com.sudoku.game.model.Cell

object SudokuValidator {

    /**
     * Returns set of (row, col) pairs that have conflicts.
     */
    fun findConflicts(cells: List<List<Cell>>): Set<Pair<Int, Int>> {
        val conflicts = mutableSetOf<Pair<Int, Int>>()

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (cells[r][c].value != 0 && hasConflict(cells, r, c)) {
                    conflicts.add(Pair(r, c))
                }
            }
        }
        return conflicts
    }

    /**
     * Checks if the cell at [row],[col] conflicts with any peer.
     */
    fun hasConflict(cells: List<List<Cell>>, row: Int, col: Int): Boolean {
        val value = cells[row][col].value
        if (value == 0) return false

        // Check row
        for (c in 0 until 9) {
            if (c != col && cells[row][c].value == value) return true
        }
        // Check column
        for (r in 0 until 9) {
            if (r != row && cells[r][col].value == value) return true
        }
        // Check box
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (r != row && c != col && cells[r][c].value == value) return true
            }
        }
        return false
    }

    /**
     * Checks if the board is completely and correctly filled (single pass).
     */
    fun isComplete(cells: List<List<Cell>>): Boolean {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = cells[r][c]
                if (cell.isEmpty) return false
                if (hasConflict(cells, r, c)) return false
            }
        }
        return true
    }

    /**
     * Counts how many times [value] appears on the board.
     */
    fun countValue(cells: List<List<Cell>>, value: Int): Int {
        var count = 0
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (cells[r][c].value == value) count++
            }
        }
        return count
    }
}
