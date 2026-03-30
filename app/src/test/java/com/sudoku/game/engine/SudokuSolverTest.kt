package com.sudoku.game.engine

import org.junit.Assert.*
import org.junit.Test

class SudokuSolverTest {

    @Test
    fun `solve completes a valid puzzle`() {
        val board = arrayOf(
            intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
            intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
            intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
            intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
            intArrayOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
            intArrayOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
            intArrayOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
            intArrayOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
            intArrayOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
        )

        assertTrue(SudokuSolver.solve(board))

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                assertNotEquals(0, board[r][c])
            }
        }
        for (r in 0 until 9) {
            assertEquals("Row $r has duplicates", 9, board[r].toSet().size)
        }
        for (c in 0 until 9) {
            val col = (0 until 9).map { board[it][c] }.toSet()
            assertEquals("Col $c has duplicates", 9, col.size)
        }
    }

    @Test
    fun `solve returns false for unsolvable puzzle`() {
        // Row 0 needs only 9, but col 8 already has 9 → dead end
        val board = arrayOf(
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 9),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)
        )
        assertFalse(SudokuSolver.solve(board))
    }

    @Test
    fun `isValid detects row conflict`() {
        val board = Array(9) { IntArray(9) }
        board[0][0] = 5
        assertFalse(SudokuSolver.isValid(board, 0, 4, 5))
    }

    @Test
    fun `isValid detects column conflict`() {
        val board = Array(9) { IntArray(9) }
        board[0][0] = 5
        assertFalse(SudokuSolver.isValid(board, 4, 0, 5))
    }

    @Test
    fun `isValid detects box conflict`() {
        val board = Array(9) { IntArray(9) }
        board[0][0] = 5
        assertFalse(SudokuSolver.isValid(board, 2, 2, 5))
    }

    @Test
    fun `isValid allows non-conflicting placement`() {
        val board = Array(9) { IntArray(9) }
        board[0][0] = 5
        assertTrue(SudokuSolver.isValid(board, 4, 4, 5))
    }

    @Test
    fun `countSolutions returns 1 for unique puzzle`() {
        val board = arrayOf(
            intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
            intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
            intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
            intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
            intArrayOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
            intArrayOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
            intArrayOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
            intArrayOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
            intArrayOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
        )
        assertEquals(1, SudokuSolver.countSolutions(board))
    }
}
