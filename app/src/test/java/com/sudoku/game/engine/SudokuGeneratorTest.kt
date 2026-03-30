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
    fun `generated solution has no conflicts`() {
        val state = SudokuGenerator.generate(Difficulty.BEGINNER)

        // Check rows
        for (r in 0 until 9) {
            val values = state.solution[r].toSet()
            assertEquals("Row $r has duplicates", 9, values.size)
            assertEquals((1..9).toSet(), values)
        }

        // Check columns
        for (c in 0 until 9) {
            val values = (0 until 9).map { state.solution[it][c] }.toSet()
            assertEquals("Col $c has duplicates", 9, values.size)
        }

        // Check boxes
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                val values = mutableSetOf<Int>()
                for (r in boxRow * 3 until boxRow * 3 + 3) {
                    for (c in boxCol * 3 until boxCol * 3 + 3) {
                        values.add(state.solution[r][c])
                    }
                }
                assertEquals("Box ($boxRow,$boxCol) has duplicates", 9, values.size)
            }
        }
    }

    private fun assertValidPuzzle(difficulty: Difficulty) {
        val state = SudokuGenerator.generate(difficulty)

        // Verify board dimensions
        assertEquals(9, state.cells.size)
        state.cells.forEach { row -> assertEquals(9, row.size) }

        // Verify solution is complete
        state.solution.forEach { row ->
            row.forEach { value ->
                assertTrue("Solution has empty cells", value in 1..9)
            }
        }

        // Verify given cells match solution
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = state.cells[r][c]
                if (cell.isGiven) {
                    assertEquals(state.solution[r][c], cell.value)
                } else {
                    assertEquals(0, cell.value)
                }
            }
        }

        // Count empty cells is within range
        val emptyCount = state.cells.flatten().count { it.isEmpty }
        assertTrue(
            "Difficulty ${difficulty.name}: $emptyCount empty not in ${difficulty.cluesToRemove}",
            emptyCount in difficulty.cluesToRemove.first..difficulty.cluesToRemove.last
        )
    }
}
