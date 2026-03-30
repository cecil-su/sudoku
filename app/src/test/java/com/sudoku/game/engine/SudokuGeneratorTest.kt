package com.sudoku.game.engine

import com.sudoku.game.model.Difficulty
import org.junit.Assert.*
import org.junit.Test

class SudokuGeneratorTest {

    @Test
    fun `generate creates valid beginner puzzle`() {
        assertValidPuzzle(Difficulty.BEGINNER)
    }

    @Test
    fun `generate creates valid easy puzzle`() {
        assertValidPuzzle(Difficulty.EASY)
    }

    @Test
    fun `generated puzzle is solvable by logic within difficulty level`() {
        val difficulty = Difficulty.BEGINNER
        val state = SudokuGenerator.generate(difficulty)
        val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
        val result = LogicSolver.analyze(board)
        assertTrue("Puzzle should be logic-solvable", result.solved)
        assertTrue(
            "Max technique ${result.maxLevel} should be <= ${difficulty.maxTechniqueLevel}",
            result.maxLevel <= difficulty.maxTechniqueLevel
        )
    }

    @Test
    fun `generated solution has no conflicts`() {
        val state = SudokuGenerator.generate(Difficulty.BEGINNER)
        for (r in 0 until 9) {
            assertEquals("Row $r has duplicates", 9, state.solution[r].toSet().size)
            assertEquals((1..9).toSet(), state.solution[r].toSet())
        }
        for (c in 0 until 9) {
            val values = (0 until 9).map { state.solution[it][c] }.toSet()
            assertEquals("Col $c has duplicates", 9, values.size)
        }
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                val values = mutableSetOf<Int>()
                for (r in boxRow * 3 until boxRow * 3 + 3)
                    for (c in boxCol * 3 until boxCol * 3 + 3)
                        values.add(state.solution[r][c])
                assertEquals("Box ($boxRow,$boxCol) has duplicates", 9, values.size)
            }
        }
    }

    private fun assertValidPuzzle(difficulty: Difficulty) {
        val state = SudokuGenerator.generate(difficulty)

        assertEquals(9, state.cells.size)
        state.cells.forEach { row -> assertEquals(9, row.size) }

        state.solution.forEach { row ->
            row.forEach { value -> assertTrue("Solution has empty cells", value in 1..9) }
        }

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = state.cells[r][c]
                if (cell.isGiven) assertEquals(state.solution[r][c], cell.value)
                else assertEquals(0, cell.value)
            }
        }

        val emptyCount = state.cells.flatten().count { it.isEmpty }
        assertTrue(
            "Difficulty ${difficulty.name}: $emptyCount empty not in ${difficulty.targetRemovals}",
            emptyCount in difficulty.targetRemovals
        )
    }
}
