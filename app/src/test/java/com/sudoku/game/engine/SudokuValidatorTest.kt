package com.sudoku.game.engine

import com.sudoku.game.model.Cell
import org.junit.Assert.*
import org.junit.Test

class SudokuValidatorTest {

    private fun createBoard(values: Array<IntArray>): List<List<Cell>> {
        return List(9) { r ->
            List(9) { c ->
                Cell(row = r, col = c, value = values[r][c])
            }
        }
    }

    @Test
    fun `hasConflict detects row duplicate`() {
        val values = Array(9) { IntArray(9) }
        values[0][0] = 5
        values[0][3] = 5
        val cells = createBoard(values)

        assertTrue(SudokuValidator.hasConflict(cells, 0, 0))
        assertTrue(SudokuValidator.hasConflict(cells, 0, 3))
    }

    @Test
    fun `hasConflict detects column duplicate`() {
        val values = Array(9) { IntArray(9) }
        values[0][0] = 3
        values[5][0] = 3
        val cells = createBoard(values)

        assertTrue(SudokuValidator.hasConflict(cells, 0, 0))
        assertTrue(SudokuValidator.hasConflict(cells, 5, 0))
    }

    @Test
    fun `hasConflict detects box duplicate`() {
        val values = Array(9) { IntArray(9) }
        values[0][0] = 7
        values[2][2] = 7
        val cells = createBoard(values)

        assertTrue(SudokuValidator.hasConflict(cells, 0, 0))
        assertTrue(SudokuValidator.hasConflict(cells, 2, 2))
    }

    @Test
    fun `hasConflict returns false for empty cell`() {
        val values = Array(9) { IntArray(9) }
        val cells = createBoard(values)

        assertFalse(SudokuValidator.hasConflict(cells, 0, 0))
    }

    @Test
    fun `hasConflict returns false for valid placement`() {
        val values = Array(9) { IntArray(9) }
        values[0][0] = 1
        values[1][3] = 1  // Different row, col, and box
        val cells = createBoard(values)

        assertFalse(SudokuValidator.hasConflict(cells, 0, 0))
        assertFalse(SudokuValidator.hasConflict(cells, 1, 3))
    }

    @Test
    fun `findConflicts returns all conflicting cells`() {
        val values = Array(9) { IntArray(9) }
        values[0][0] = 5
        values[0][8] = 5  // Row conflict
        values[4][4] = 3
        values[4][7] = 3  // Row conflict
        val cells = createBoard(values)

        val conflicts = SudokuValidator.findConflicts(cells)
        assertTrue(conflicts.contains(Pair(0, 0)))
        assertTrue(conflicts.contains(Pair(0, 8)))
        assertTrue(conflicts.contains(Pair(4, 4)))
        assertTrue(conflicts.contains(Pair(4, 7)))
    }

    @Test
    fun `isComplete returns false for incomplete board`() {
        val values = Array(9) { IntArray(9) }
        val cells = createBoard(values)
        assertFalse(SudokuValidator.isComplete(cells))
    }

    @Test
    fun `countValue counts correctly`() {
        val values = Array(9) { IntArray(9) }
        values[0][0] = 5
        values[3][4] = 5
        values[8][8] = 5
        val cells = createBoard(values)

        assertEquals(3, SudokuValidator.countValue(cells, 5))
        assertEquals(0, SudokuValidator.countValue(cells, 1))
    }
}
