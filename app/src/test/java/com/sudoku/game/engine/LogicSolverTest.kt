package com.sudoku.game.engine

import org.junit.Assert.*
import org.junit.Test

class LogicSolverTest {

    @Test
    fun `analyze solves a simple puzzle with naked and hidden singles`() {
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
        val result = LogicSolver.analyze(board)
        assertTrue("Should be solvable by logic", result.solved)
        assertTrue("Should use only basic techniques", result.maxLevel <= 2)
    }

    @Test
    fun `analyzeWithCap respects level cap`() {
        // Generate a beginner puzzle — should be solvable within level 2
        val state = SudokuGenerator.generate(com.sudoku.game.model.Difficulty.BEGINNER)
        val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }

        val result = LogicSolver.analyzeWithCap(board, 2)
        assertTrue("Beginner puzzle should be solvable with level 2", result.solved)
    }

    @Test
    fun `analyze returns not solved for empty board`() {
        val board = Array(9) { IntArray(9) }
        val result = LogicSolver.analyze(board)
        assertFalse("Empty board should not be logic-solvable", result.solved)
    }

    @Test
    fun `analyze returns solved with level 0 for complete board`() {
        val state = SudokuGenerator.generate(com.sudoku.game.model.Difficulty.BEGINNER)
        val board = Array(9) { r -> IntArray(9) { c -> state.solution[r][c] } }
        val result = LogicSolver.analyze(board)
        assertTrue(result.solved)
        assertEquals(0, result.maxLevel)
    }
}
