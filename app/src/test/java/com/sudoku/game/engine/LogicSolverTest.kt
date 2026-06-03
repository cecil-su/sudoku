package com.sudoku.game.engine

import com.sudoku.game.model.DemoStep
import com.sudoku.game.model.Difficulty
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

    @Test
    fun `findHint progressively solves a singles-only puzzle`() {
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
        var guard = 0
        while (!isFull(board) && guard++ < 81) {
            val hint = LogicSolver.findHint(board)
            assertNotNull("Should find a step until solved", hint)
            val p = hint!!.placement
            assertNotNull("Singles puzzle should yield placements", p)
            assertTrue("Hint must be a legal placement", isLegal(board, p!!.row, p.col, p.value))
            board[p.row][p.col] = p.value
        }
        assertTrue("Board should be fully solved", isFull(board))
        assertTrue("Solution must be valid", isValidSolution(board))
    }

    @Test
    fun `findHint returns a step for fresh generated puzzles`() {
        for (d in Difficulty.values()) {
            val state = SudokuGenerator.generate(d)
            val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
            assertNotNull("${d.name}: should find a hint on a fresh puzzle", LogicSolver.findHint(board))
        }
    }

    @Test
    fun `findHint placements always match the unique solution`() {
        val state = SudokuGenerator.generate(Difficulty.MEDIUM)
        val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
        repeat(30) {
            val p = LogicSolver.findHint(board)?.placement ?: return@repeat
            assertEquals(
                "Logical placement must equal the unique solution",
                state.solution[p.row][p.col], p.value
            )
            board[p.row][p.col] = p.value
        }
    }

    // ---- Advanced technique detectors (levels 3-6) ----
    // Driven by hand-built candidate grids so each detector is exercised directly;
    // stepForward() resolves singles first and would otherwise mask these patterns.
    // Each asserts the struck candidates (eliminatedCells) — the demo's key frame.

    @Test
    fun `pointingClaimingStep detects a pointing pattern`() {
        val cands = emptyCands()
        // Box 0: candidate 1 appears only in row-0 cells (0,0),(0,1); (0,4) outside the box keeps 1.
        cands[0][0] = bits(1, 3)
        cands[0][1] = bits(1, 4)
        cands[0][2] = bits(3, 4)
        cands[0][4] = bits(1, 5)
        val step = LogicSolver.pointingClaimingStep(cands)
        assertNotNull(step)
        assertEquals("区块排除", step!!.techniqueName)
        assertNull(step.placement)
        assertEquals(setOf(0 to 0, 0 to 1), step.lookCells.toSet())
        assertEquals(listOf(DemoStep.Elimination(0, 4, listOf(1))), step.eliminatedCells)
    }

    @Test
    fun `nakedSubsetStep detects a naked pair`() {
        val cands = emptyCands()
        // Row 0: (0,0) and (0,1) form the pair {1,2}; (0,2) shares candidate 1 → eliminable.
        cands[0][0] = bits(1, 2)
        cands[0][1] = bits(1, 2)
        cands[0][2] = bits(1, 3)
        val step = LogicSolver.nakedSubsetStep(cands)
        assertNotNull(step)
        assertEquals("显性数对", step!!.techniqueName)
        assertNull(step.placement)
        assertEquals(setOf(0 to 0, 0 to 1), step.lookCells.toSet())
        assertEquals(listOf(DemoStep.Elimination(0, 2, listOf(1))), step.eliminatedCells)
    }

    @Test
    fun `hiddenSubsetStep detects a hidden pair`() {
        val cands = emptyCands()
        // Row 0: values 4 and 5 occur only in (0,0),(0,1), which also carry extra candidates.
        cands[0][0] = bits(4, 5, 7)
        cands[0][1] = bits(4, 5, 8)
        cands[0][2] = bits(7, 8)
        val step = LogicSolver.hiddenSubsetStep(cands)
        assertNotNull(step)
        assertEquals("隐性数对", step!!.techniqueName)
        assertNull(step.placement)
        assertEquals(setOf(0 to 0, 0 to 1), step.lookCells.toSet())
        // The pattern cells keep only {4,5}; their extra candidates (7 / 8) are struck.
        assertEquals(
            listOf(DemoStep.Elimination(0, 0, listOf(7)), DemoStep.Elimination(0, 1, listOf(8))),
            step.eliminatedCells
        )
    }

    @Test
    fun `xWingStep detects an x-wing`() {
        val cands = emptyCands()
        // Value 5 in rows 1 & 4 sits only in cols 2 & 7; (0,2) keeps 5 → eliminable.
        cands[1][2] = bits(5, 1)
        cands[1][7] = bits(5, 2)
        cands[4][2] = bits(5, 3)
        cands[4][7] = bits(5, 4)
        cands[0][2] = bits(5, 9)
        val step = LogicSolver.xWingStep(cands)
        assertNotNull(step)
        assertEquals("X-Wing", step!!.techniqueName)
        assertNull(step.placement)
        assertEquals(setOf(1 to 2, 1 to 7, 4 to 2, 4 to 7), step.lookCells.toSet())
        assertEquals(listOf(DemoStep.Elimination(0, 2, listOf(5))), step.eliminatedCells)
    }

    // ---- Demo trajectory (the shared-kernel timeline that drives the demo) ----

    @Test
    fun `demoTrajectory solves every difficulty to a legal terminal`() {
        // Carrying candidate state forward lets the trajectory chain "eliminate →
        // unlocks a single", so even HARD/EXPERT puzzles (which need eliminations)
        // run all the way to a full, valid board.
        for (d in Difficulty.values()) {
            val state = SudokuGenerator.generate(d)
            val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
            val steps = LogicSolver.demoTrajectory(board)
            assertTrue("${d.name}: trajectory should be non-empty", steps.isNotEmpty())

            // Replay placements onto a copy (eliminations don't change board values).
            val replay = Array(9) { board[it].copyOf() }
            for (s in steps) s.placement?.let { p ->
                assertTrue("${d.name}: placement must be legal", isLegal(replay, p.row, p.col, p.value))
                assertEquals(
                    "${d.name}: placement must match the unique solution",
                    state.solution[p.row][p.col], p.value
                )
                replay[p.row][p.col] = p.value
            }
            assertTrue("${d.name}: trajectory should reach a full board", isFull(replay))
            assertTrue("${d.name}: terminal board must be a valid solution", isValidSolution(replay))
        }
    }

    @Test
    fun `every demo step is self-consistent`() {
        val state = SudokuGenerator.generate(Difficulty.EXPERT)
        val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
        for (s in LogicSolver.demoTrajectory(board)) {
            assertTrue("level in 1..6", s.level in 1..6)
            assertTrue("look cells non-empty", s.lookCells.isNotEmpty())
            assertTrue("narration non-blank", s.narration.isNotBlank())
            // A step either fills a single or strikes candidates — never both, never neither.
            if (s.placement != null) {
                assertTrue("singles strike nothing", s.eliminatedCells.isEmpty())
                assertTrue("a placement is a level 1-2 single", s.level <= 2)
            } else {
                assertTrue("an elimination step strikes at least one candidate", s.eliminatedCells.isNotEmpty())
                assertTrue("each elimination carries values", s.eliminatedCells.all { it.values.isNotEmpty() })
                assertTrue("an elimination is a level 3-6 technique", s.level >= 3)
            }
        }
    }

    @Test
    fun `findHint first step matches the trajectory first step`() {
        // Proves findHint and the trajectory share one detection kernel.
        for (d in Difficulty.values()) {
            val state = SudokuGenerator.generate(d)
            val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
            val hint = LogicSolver.findHint(board)
            val first = LogicSolver.demoTrajectory(board).firstOrNull()
            assertNotNull("${d.name}: findHint should return a step", hint)
            assertNotNull("${d.name}: trajectory should have a first step", first)
            assertEquals("${d.name}: same technique", first!!.techniqueName, hint!!.techniqueName)
            assertEquals("${d.name}: same look cells", first.lookCells, hint.highlightCells)
            assertEquals(
                "${d.name}: same placement",
                first.placement?.let { Triple(it.row, it.col, it.value) },
                hint.placement?.let { Triple(it.row, it.col, it.value) }
            )
        }
    }

    @Test
    fun `analyze and demoTrajectory stay in lockstep`() {
        // The third kernel consumer: analyze() walks the same stepForward+applyStep
        // loop, so its maxLevel must equal the max level across the trajectory's steps.
        for (d in Difficulty.values()) {
            val state = SudokuGenerator.generate(d)
            val board = Array(9) { r -> IntArray(9) { c -> state.cells[r][c].value } }
            val result = LogicSolver.analyze(board)
            val steps = LogicSolver.demoTrajectory(board)
            assertTrue("${d.name}: analyze should solve the puzzle", result.solved)
            assertTrue("${d.name}: trajectory should be non-empty", steps.isNotEmpty())
            assertEquals(
                "${d.name}: analyze maxLevel must equal the trajectory's max step level",
                result.maxLevel, steps.maxOf { it.level }
            )
        }
    }

    private fun emptyCands(): Array<IntArray> = Array(9) { IntArray(9) }

    private fun bits(vararg values: Int): Int = values.fold(0) { acc, v -> acc or (1 shl (v - 1)) }

    private fun isFull(board: Array<IntArray>): Boolean =
        board.all { row -> row.all { it != 0 } }

    private fun isLegal(board: Array<IntArray>, r: Int, c: Int, v: Int): Boolean {
        for (i in 0 until 9) {
            if (i != c && board[r][i] == v) return false
            if (i != r && board[i][c] == v) return false
        }
        val br = (r / 3) * 3; val bc = (c / 3) * 3
        for (rr in br until br + 3) for (cc in bc until bc + 3) {
            if ((rr != r || cc != c) && board[rr][cc] == v) return false
        }
        return true
    }

    private fun isValidSolution(board: Array<IntArray>): Boolean {
        for (i in 0 until 9) {
            val row = mutableSetOf<Int>(); val col = mutableSetOf<Int>()
            for (j in 0 until 9) { row.add(board[i][j]); col.add(board[j][i]) }
            if (row.size != 9 || col.size != 9) return false
        }
        for (br in 0 until 3) for (bc in 0 until 3) {
            val box = mutableSetOf<Int>()
            for (r in br * 3 until br * 3 + 3) for (c in bc * 3 until bc * 3 + 3) box.add(board[r][c])
            if (box.size != 9) return false
        }
        return true
    }
}
