package com.sudoku.game.engine

import com.sudoku.game.model.Cell
import com.sudoku.game.model.Difficulty
import com.sudoku.game.model.GameState
import kotlin.random.Random

object SudokuGenerator {

    /**
     * Generates a new game with the given difficulty.
     */
    fun generate(difficulty: Difficulty): GameState {
        val solution = generateFullBoard()
        val puzzle = createPuzzle(solution, difficulty)
        val cells = List(9) { row ->
            List(9) { col ->
                val value = puzzle[row][col]
                Cell(
                    row = row,
                    col = col,
                    value = value,
                    isGiven = value != 0
                )
            }
        }
        val solutionList = solution.map { it.toList() }
        return GameState(
            cells = cells,
            solution = solutionList,
            difficulty = difficulty
        )
    }

    /**
     * Generates a complete valid 9x9 board.
     * Strategy: fill diagonal boxes first (independent), then solve the rest.
     */
    private fun generateFullBoard(): Array<IntArray> {
        val board = Array(9) { IntArray(9) }

        // Fill three diagonal 3x3 boxes (they don't affect each other)
        for (box in 0 until 3) {
            fillBox(board, box * 3, box * 3)
        }

        // Solve the rest using backtracking
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
        val numbers = (1..9).shuffled()

        for (num in numbers) {
            if (SudokuSolver.isValid(board, row, col, num)) {
                board[row][col] = num
                if (solveRandom(board)) return true
                board[row][col] = 0
            }
        }
        return false
    }

    /**
     * Creates a puzzle by removing cells from the complete board.
     * Uses a fast approach: try to solve with one cell removed and a different
     * value in that cell. If it fails, the original value is forced (unique).
     */
    private fun createPuzzle(solution: Array<IntArray>, difficulty: Difficulty): Array<IntArray> {
        val puzzle = Array(9) { solution[it].copyOf() }
        val target = Random.nextInt(difficulty.cluesToRemove.first, difficulty.cluesToRemove.last + 1)

        // Shuffle positions for randomness
        val positions = (0 until 81).shuffled()

        var removed = 0
        for (pos in positions) {
            if (removed >= target) break
            val row = pos / 9
            val col = pos % 9
            if (puzzle[row][col] == 0) continue

            val backup = puzzle[row][col]
            puzzle[row][col] = 0

            // Fast unique check: try every other value in this cell.
            // If none leads to a valid complete board, the solution is unique.
            if (hasUniqueSolution(puzzle, row, col, backup)) {
                removed++
            } else {
                puzzle[row][col] = backup
            }
        }

        return puzzle
    }

    /**
     * Checks if the puzzle has a unique solution by testing if any value
     * other than [correctValue] at [row],[col] can produce a valid solution.
     * This is much faster than full countSolutions for incremental removal.
     */
    private fun hasUniqueSolution(puzzle: Array<IntArray>, row: Int, col: Int, correctValue: Int): Boolean {
        for (num in 1..9) {
            if (num == correctValue) continue
            if (!SudokuSolver.isValid(puzzle, row, col, num)) continue

            puzzle[row][col] = num
            val copy = Array(9) { puzzle[it].copyOf() }
            val solvable = SudokuSolver.solve(copy)
            puzzle[row][col] = 0

            if (solvable) return false // Another solution exists
        }
        return true // Only the correct value works
    }

    private fun findEmpty(board: Array<IntArray>): Pair<Int, Int>? {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (board[r][c] == 0) return Pair(r, c)
            }
        }
        return null
    }
}
