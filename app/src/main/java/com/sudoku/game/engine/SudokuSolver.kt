package com.sudoku.game.engine

object SudokuSolver {

    /**
     * Solves the puzzle in-place using backtracking with MRV heuristic.
     * Returns true if a solution exists.
     */
    fun solve(board: Array<IntArray>): Boolean {
        val empty = findBestEmpty(board) ?: return true
        val (row, col) = empty

        for (num in 1..9) {
            if (isValid(board, row, col, num)) {
                board[row][col] = num
                if (solve(board)) return true
                board[row][col] = 0
            }
        }
        return false
    }

    /**
     * Counts the number of solutions (stops at [limit] for efficiency).
     * Uses MRV (Minimum Remaining Values) heuristic for speed.
     */
    fun countSolutions(board: Array<IntArray>, limit: Int = 2): Int {
        val empty = findBestEmpty(board) ?: return 1
        val (row, col) = empty
        var count = 0

        for (num in 1..9) {
            if (isValid(board, row, col, num)) {
                board[row][col] = num
                count += countSolutions(board, limit)
                board[row][col] = 0
                if (count >= limit) return count
            }
        }
        return count
    }

    /**
     * Checks if placing [num] at [row],[col] is valid.
     */
    fun isValid(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row
        for (c in 0 until 9) {
            if (board[row][c] == num) return false
        }
        // Check column
        for (r in 0 until 9) {
            if (board[r][col] == num) return false
        }
        // Check 3x3 box
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (board[r][c] == num) return false
            }
        }
        return true
    }

    /**
     * Finds the empty cell with the fewest candidates (MRV heuristic).
     * This dramatically speeds up backtracking by pruning early.
     */
    private fun findBestEmpty(board: Array<IntArray>): Pair<Int, Int>? {
        var bestRow = -1
        var bestCol = -1
        var bestCount = 10

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (board[r][c] == 0) {
                    var count = 0
                    for (num in 1..9) {
                        if (isValid(board, r, c, num)) count++
                    }
                    if (count == 0) return Pair(r, c) // Dead end, fail fast
                    if (count < bestCount) {
                        bestCount = count
                        bestRow = r
                        bestCol = c
                    }
                }
            }
        }
        return if (bestRow == -1) null else Pair(bestRow, bestCol)
    }
}
