package com.sudoku.game.engine

import com.sudoku.game.model.Cell
import com.sudoku.game.model.Difficulty
import com.sudoku.game.model.GameState

object SudokuGenerator {

    /**
     * Generates a new game with the given difficulty.
     * Uses LogicSolver to ensure the puzzle is solvable with techniques
     * at or below the difficulty's maximum technique level.
     */
    fun generate(difficulty: Difficulty): GameState {
        val solution = generateFullBoard()
        val puzzle = createPuzzle(solution, difficulty)
        val cells = List(9) { row ->
            List(9) { col ->
                val value = puzzle[row][col]
                Cell(row = row, col = col, value = value, isGiven = value != 0)
            }
        }
        return GameState(
            cells = cells,
            solution = solution.map { it.toList() },
            difficulty = difficulty
        )
    }

    private fun generateFullBoard(): Array<IntArray> {
        val board = Array(9) { IntArray(9) }
        for (box in 0 until 3) fillBox(board, box * 3, box * 3)
        solveRandom(board)
        return board
    }

    private fun fillBox(board: Array<IntArray>, startRow: Int, startCol: Int) {
        val nums = (1..9).shuffled()
        var idx = 0
        for (r in startRow until startRow + 3) {
            for (c in startCol until startCol + 3) {
                board[r][c] = nums[idx++]
            }
        }
    }

    private fun solveRandom(board: Array<IntArray>): Boolean {
        val empty = findEmpty(board) ?: return true
        val (row, col) = empty
        for (num in (1..9).shuffled()) {
            if (SudokuSolver.isValid(board, row, col, num)) {
                board[row][col] = num
                if (solveRandom(board)) return true
                board[row][col] = 0
            }
        }
        return false
    }

    /**
     * Creates a puzzle by removing cells from the solution.
     * After each removal, verifies the puzzle is still solvable
     * using only techniques within the difficulty's allowed level.
     * Logic solvability guarantees unique solution (no guessing needed).
     */
    private fun createPuzzle(solution: Array<IntArray>, difficulty: Difficulty): Array<IntArray> {
        val puzzle = Array(9) { solution[it].copyOf() }
        val positions = (0 until 81).shuffled()
        val maxLevel = difficulty.maxTechniqueLevel
        val maxRemovals = difficulty.targetRemovals.last

        var removed = 0
        for (pos in positions) {
            if (removed >= maxRemovals) break

            val row = pos / 9
            val col = pos % 9
            if (puzzle[row][col] == 0) continue

            val backup = puzzle[row][col]
            puzzle[row][col] = 0

            val result = LogicSolver.analyzeWithCap(puzzle, maxLevel)
            if (result.solved) {
                removed++
            } else {
                puzzle[row][col] = backup
            }
        }

        // If we didn't reach minimum removals, the puzzle is still valid
        // but may be easier than intended. This is rare for lower difficulties.
        return puzzle
    }

    private fun findEmpty(board: Array<IntArray>): Pair<Int, Int>? {
        for (r in 0 until 9) for (c in 0 until 9) if (board[r][c] == 0) return Pair(r, c)
        return null
    }
}
