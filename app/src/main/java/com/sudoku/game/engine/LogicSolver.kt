package com.sudoku.game.engine

import com.sudoku.game.model.DemoStep
import com.sudoku.game.model.Hint

/**
 * Human-strategy Sudoku solver that applies techniques in order of difficulty.
 *
 * Technique levels:
 * 1 - Naked Single: cell has exactly one candidate
 * 2 - Hidden Single: value has only one possible position in a unit
 * 3 - Pointing/Claiming: box-line reduction
 * 4 - Naked Pair/Triple: N cells share N candidates in a unit
 * 5 - Hidden Pair: two values appear in only two cells of a unit
 * 6 - X-Wing: rectangle pattern elimination
 *
 * All three consumers share a single detection kernel — [stepForward] — so they
 * can never drift apart:
 *  - [analyze] / [analyzeWithCap]: grade a puzzle's difficulty (generator hot path).
 *  - [findHint]: the next single read-only step, projected to a [Hint] for the UI.
 *  - [demoTrajectory]: the full step-by-step timeline that drives the demo player.
 *
 * [stepForward] only *detects* a step (no mutation); [applyStep] advances the
 * board/candidate state. Walking the two in a loop carries candidate state
 * forward, so the trajectory can chain "eliminate → unlocks a single" — the
 * chain-break limitation of the old stateless hint path.
 */
object LogicSolver {

    data class SolveResult(val solved: Boolean, val maxLevel: Int)

    private const val ALL = 0x1FF // bits 0-8 = values 1-9
    private const val MAX_STEPS = 1000 // safety bound; progress is guaranteed each step

    /**
     * Analyzes the puzzle and returns whether it's solvable by logic
     * and the maximum technique level required.
     * Does NOT modify the input board.
     */
    fun analyze(board: Array<IntArray>): SolveResult {
        val copy = Array(9) { board[it].copyOf() }
        val cands = initCandidates(copy)
        var maxLevel = 0

        while (true) {
            if (isSolved(copy)) return SolveResult(true, maxLevel)

            val step = stepForward(copy, cands) ?: return SolveResult(false, maxLevel)
            maxLevel = maxOf(maxLevel, step.level)
            applyStep(copy, cands, step)
        }
    }

    /**
     * Analyzes with a cap on technique level. Returns immediately if
     * a technique above maxAllowed would be needed.
     */
    fun analyzeWithCap(board: Array<IntArray>, maxAllowed: Int): SolveResult {
        val copy = Array(9) { board[it].copyOf() }
        val cands = initCandidates(copy)
        var maxLevel = 0

        while (true) {
            if (isSolved(copy)) return SolveResult(true, maxLevel)

            val step = stepForward(copy, cands)
            if (step == null || step.level > maxAllowed) return SolveResult(false, maxLevel)
            maxLevel = maxOf(maxLevel, step.level)
            applyStep(copy, cands, step)
        }
    }

    // ============================================================
    // Hint / demo
    //
    // [findHint] returns the next deductive step a human would take from the
    // CURRENT board, read-only. [demoTrajectory] returns the whole timeline,
    // carrying candidate state forward so elimination steps that unlock later
    // singles are preserved. Both run the same [stepForward] kernel as [analyze],
    // so the demo can never narrate a step the grader wouldn't take.
    // ============================================================

    /**
     * Returns the next deductive step as a teaching [Hint], or null if no
     * technique (levels 1-6) applies to the current board. Read-only.
     * By construction this is the first step of [demoTrajectory].
     */
    fun findHint(board: Array<IntArray>): Hint? =
        stepForward(board, initCandidates(board))?.toHint()

    /**
     * Returns the full ordered list of deductive steps that solve [board] from
     * its current state, one [DemoStep] per technique application. Read-only.
     * Empty if the board is already solved; ends early (partial) only if the
     * board is not logic-solvable, which never happens for puzzles this app
     * generates.
     */
    fun demoTrajectory(board: Array<IntArray>): List<DemoStep> {
        val copy = Array(9) { board[it].copyOf() }
        val cands = initCandidates(copy)
        val steps = mutableListOf<DemoStep>()

        var guard = 0
        while (!isSolved(copy) && guard++ < MAX_STEPS) {
            val step = stepForward(copy, cands) ?: break
            steps.add(step)
            applyStep(copy, cands, step)
        }
        return steps
    }

    // ============================================================
    // Shared detection kernel
    // ============================================================

    /**
     * Detects the next applicable step from the current (board, candidates)
     * state, in technique-priority order. Pure: never mutates its inputs.
     * Returns null when no technique (levels 1-6) applies.
     */
    private fun stepForward(board: Array<IntArray>, cands: Array<IntArray>): DemoStep? {
        nakedSingleStep(board, cands)?.let { return it }
        hiddenSingleStep(cands)?.let { return it }
        pointingClaimingStep(cands)?.let { return it }
        nakedSubsetStep(cands)?.let { return it }
        hiddenSubsetStep(cands)?.let { return it }
        xWingStep(cands)?.let { return it }
        return null
    }

    /** Advances board/candidate state by one detected [step]. */
    private fun applyStep(board: Array<IntArray>, cands: Array<IntArray>, step: DemoStep) {
        val p = step.placement
        if (p != null) {
            place(board, cands, p.row, p.col, p.value)
        } else {
            for (e in step.eliminatedCells) {
                for (v in e.values) cands[e.row][e.col] = cands[e.row][e.col] and (1 shl (v - 1)).inv()
            }
        }
    }

    // ============================================================
    // Detectors — each returns a DemoStep without mutating `cands`.
    // The four advanced ones are `internal` so they can be unit-tested in
    // isolation with a hand-built candidate grid (singles would otherwise
    // mask them).
    // ============================================================

    private fun nakedSingleStep(board: Array<IntArray>, cands: Array<IntArray>): DemoStep? {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (board[r][c] == 0 && Integer.bitCount(cands[r][c]) == 1) {
                    val v = Integer.numberOfTrailingZeros(cands[r][c]) + 1
                    return DemoStep(
                        1, "唯一余数",
                        listOf(r to c), emptyList(),
                        DemoStep.Placement(r, c, v),
                        "${posText(r, c)}的同行、同列、同宫已经用掉了其他 8 个数字，所以这一格只能填 $v。"
                    )
                }
            }
        }
        return null
    }

    private fun hiddenSingleStep(cands: Array<IntArray>): DemoStep? {
        for (r in 0 until 9) {
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                var count = 0; var lastC = -1
                for (c in 0 until 9) if (cands[r][c] and bit != 0) { count++; lastC = c }
                if (count == 1) return hiddenSingleStepOf(r, lastC, v, "第${r + 1}行", rowCells(r))
            }
        }
        for (c in 0 until 9) {
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                var count = 0; var lastR = -1
                for (r in 0 until 9) if (cands[r][c] and bit != 0) { count++; lastR = r }
                if (count == 1) return hiddenSingleStepOf(lastR, c, v, "第${c + 1}列", colCells(c))
            }
        }
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                for (v in 1..9) {
                    val bit = 1 shl (v - 1)
                    var count = 0; var lastR = -1; var lastC = -1
                    for (r in br * 3 until br * 3 + 3) {
                        for (c in bc * 3 until bc * 3 + 3) {
                            if (cands[r][c] and bit != 0) { count++; lastR = r; lastC = c }
                        }
                    }
                    if (count == 1) return hiddenSingleStepOf(lastR, lastC, v, "第${br * 3 + bc + 1}宫", boxCells(br, bc))
                }
            }
        }
        return null
    }

    private fun hiddenSingleStepOf(r: Int, c: Int, v: Int, unitName: String, unitCells: List<Pair<Int, Int>>): DemoStep =
        DemoStep(
            2, "隐性唯一数",
            unitCells, emptyList(),
            DemoStep.Placement(r, c, v),
            "在${unitName}里，数字 $v 只有${posText(r, c)}这一处能放下——其余空格都因同行/列/宫已存在 $v 而被排除。"
        )

    internal fun pointingClaimingStep(cands: Array<IntArray>): DemoStep? {
        // Pointing: in a box, candidate v confined to one row/col → strike v from rest of that line
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                for (v in 1..9) {
                    val bit = 1 shl (v - 1)
                    var rowMask = 0; var colMask = 0
                    val cells = mutableListOf<Pair<Int, Int>>()
                    for (r in br * 3 until br * 3 + 3) {
                        for (c in bc * 3 until bc * 3 + 3) {
                            if (cands[r][c] and bit != 0) {
                                rowMask = rowMask or (1 shl r); colMask = colMask or (1 shl c)
                                cells.add(r to c)
                            }
                        }
                    }
                    if (Integer.bitCount(rowMask) == 1) {
                        val row = Integer.numberOfTrailingZeros(rowMask)
                        val elim = (0 until 9)
                            .filter { it / 3 != bc && cands[row][it] and bit != 0 }
                            .map { DemoStep.Elimination(row, it, listOf(v)) }
                        if (elim.isNotEmpty()) return DemoStep(
                            3, "区块排除", cells, elim, null,
                            "第${br * 3 + bc + 1}宫中，候选数 $v 只出现在第${row + 1}行 → 第${row + 1}行其余格子都不能填 $v，可以擦掉它们的 $v 候选。"
                        )
                    }
                    if (Integer.bitCount(colMask) == 1) {
                        val col = Integer.numberOfTrailingZeros(colMask)
                        val elim = (0 until 9)
                            .filter { it / 3 != br && cands[it][col] and bit != 0 }
                            .map { DemoStep.Elimination(it, col, listOf(v)) }
                        if (elim.isNotEmpty()) return DemoStep(
                            3, "区块排除", cells, elim, null,
                            "第${br * 3 + bc + 1}宫中，候选数 $v 只出现在第${col + 1}列 → 第${col + 1}列其余格子都不能填 $v。"
                        )
                    }
                }
            }
        }
        // Claiming: in a row/col, candidate v confined to one box → strike v from rest of box
        for (v in 1..9) {
            val bit = 1 shl (v - 1)
            for (r in 0 until 9) {
                var boxMask = 0
                val cells = mutableListOf<Pair<Int, Int>>()
                for (c in 0 until 9) if (cands[r][c] and bit != 0) { boxMask = boxMask or (1 shl (c / 3)); cells.add(r to c) }
                if (Integer.bitCount(boxMask) == 1) {
                    val bc = Integer.numberOfTrailingZeros(boxMask)
                    val br = r / 3
                    val elim = mutableListOf<DemoStep.Elimination>()
                    for (rr in br * 3 until br * 3 + 3) {
                        if (rr != r) for (cc in bc * 3 until bc * 3 + 3) {
                            if (cands[rr][cc] and bit != 0) elim.add(DemoStep.Elimination(rr, cc, listOf(v)))
                        }
                    }
                    if (elim.isNotEmpty()) return DemoStep(
                        3, "区块排除", cells, elim, null,
                        "第${r + 1}行中，候选数 $v 只落在第${br * 3 + bc + 1}宫内 → 该宫其他格子都不能填 $v。"
                    )
                }
            }
            for (c in 0 until 9) {
                var boxMask = 0
                val cells = mutableListOf<Pair<Int, Int>>()
                for (r in 0 until 9) if (cands[r][c] and bit != 0) { boxMask = boxMask or (1 shl (r / 3)); cells.add(r to c) }
                if (Integer.bitCount(boxMask) == 1) {
                    val br = Integer.numberOfTrailingZeros(boxMask)
                    val bc = c / 3
                    val elim = mutableListOf<DemoStep.Elimination>()
                    for (cc in bc * 3 until bc * 3 + 3) {
                        if (cc != c) for (rr in br * 3 until br * 3 + 3) {
                            if (cands[rr][cc] and bit != 0) elim.add(DemoStep.Elimination(rr, cc, listOf(v)))
                        }
                    }
                    if (elim.isNotEmpty()) return DemoStep(
                        3, "区块排除", cells, elim, null,
                        "第${c + 1}列中，候选数 $v 只落在第${br * 3 + bc + 1}宫内 → 该宫其他格子都不能填 $v。"
                    )
                }
            }
        }
        return null
    }

    internal fun nakedSubsetStep(cands: Array<IntArray>): DemoStep? {
        for (unit in allUnits()) {
            val empty = unit.filter { (r, c) -> cands[r][c] != 0 }
            if (empty.size < 3) continue
            for (i in empty.indices) {
                for (j in i + 1 until empty.size) {
                    val union = cands[empty[i].first][empty[i].second] or cands[empty[j].first][empty[j].second]
                    if (Integer.bitCount(union) != 2) continue
                    val elim = empty.filterIndexed { k, _ -> k != i && k != j }
                        .mapNotNull { (r, c) -> elimOf(r, c, cands[r][c] and union) }
                    if (elim.isNotEmpty()) {
                        val pair = listOf(empty[i], empty[j])
                        return DemoStep(
                            4, "显性数对", pair, elim, null,
                            "在${unitLabel(pair)}里，这两格都只能是 ${valuesText(union)} → 同单元其他格子不能再用这两个数，可擦掉对应候选。"
                        )
                    }
                }
            }
            for (i in empty.indices) {
                for (j in i + 1 until empty.size) {
                    for (k in j + 1 until empty.size) {
                        val union = cands[empty[i].first][empty[i].second] or
                            cands[empty[j].first][empty[j].second] or
                            cands[empty[k].first][empty[k].second]
                        if (Integer.bitCount(union) != 3) continue
                        val elim = empty.filterIndexed { m, _ -> m != i && m != j && m != k }
                            .mapNotNull { (r, c) -> elimOf(r, c, cands[r][c] and union) }
                        if (elim.isNotEmpty()) {
                            val trip = listOf(empty[i], empty[j], empty[k])
                            return DemoStep(
                                4, "显性三数组", trip, elim, null,
                                "在${unitLabel(trip)}里，这三格的候选合起来只有 ${valuesText(union)} → 它们锁定了这三个数字，同单元其他格子可排除这些候选。"
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    internal fun hiddenSubsetStep(cands: Array<IntArray>): DemoStep? {
        for (unit in allUnits()) {
            val empty = unit.filter { (r, c) -> cands[r][c] != 0 }
            if (empty.size < 3) continue
            for (v1 in 1..9) {
                val b1 = 1 shl (v1 - 1)
                for (v2 in v1 + 1..9) {
                    val b2 = 1 shl (v2 - 1)
                    val cells = empty.filter { (r, c) -> cands[r][c] and b1 != 0 || cands[r][c] and b2 != 0 }
                    if (cells.size != 2) continue
                    val both = cells.all { (r, c) -> cands[r][c] and b1 != 0 } && cells.all { (r, c) -> cands[r][c] and b2 != 0 }
                    if (!both) continue
                    val mask = b1 or b2
                    val elim = cells.mapNotNull { (r, c) -> elimOf(r, c, cands[r][c] and mask.inv() and ALL) }
                    if (elim.isNotEmpty()) return DemoStep(
                        5, "隐性数对", cells, elim, null,
                        "在${unitLabel(cells)}里，数字 $v1 和 $v2 只可能落在这两格 → 这两格中的其他候选数都可以排除。"
                    )
                }
            }
        }
        return null
    }

    internal fun xWingStep(cands: Array<IntArray>): DemoStep? {
        for (v in 1..9) {
            val bit = 1 shl (v - 1)
            for (r1 in 0 until 9) {
                val cols1 = (0 until 9).filter { cands[r1][it] and bit != 0 }
                if (cols1.size != 2) continue
                for (r2 in r1 + 1 until 9) {
                    val cols2 = (0 until 9).filter { cands[r2][it] and bit != 0 }
                    if (cols2 != cols1) continue
                    val elim = mutableListOf<DemoStep.Elimination>()
                    for (r in 0 until 9) if (r != r1 && r != r2) for (c in cols1) {
                        if (cands[r][c] and bit != 0) elim.add(DemoStep.Elimination(r, c, listOf(v)))
                    }
                    if (elim.isNotEmpty()) {
                        val corners = listOf(r1 to cols1[0], r1 to cols1[1], r2 to cols1[0], r2 to cols1[1])
                        return DemoStep(
                            6, "X-Wing", corners, elim, null,
                            "数字 $v 在第${r1 + 1}、${r2 + 1}行都只出现在第${cols1[0] + 1}、${cols1[1] + 1}列 → 这两列其他行的 $v 候选都可排除。"
                        )
                    }
                }
            }
            for (c1 in 0 until 9) {
                val rows1 = (0 until 9).filter { cands[it][c1] and bit != 0 }
                if (rows1.size != 2) continue
                for (c2 in c1 + 1 until 9) {
                    val rows2 = (0 until 9).filter { cands[it][c2] and bit != 0 }
                    if (rows2 != rows1) continue
                    val elim = mutableListOf<DemoStep.Elimination>()
                    for (c in 0 until 9) if (c != c1 && c != c2) for (r in rows1) {
                        if (cands[r][c] and bit != 0) elim.add(DemoStep.Elimination(r, c, listOf(v)))
                    }
                    if (elim.isNotEmpty()) {
                        val corners = listOf(rows1[0] to c1, rows1[1] to c1, rows1[0] to c2, rows1[1] to c2)
                        return DemoStep(
                            6, "X-Wing", corners, elim, null,
                            "数字 $v 在第${c1 + 1}、${c2 + 1}列都只出现在第${rows1[0] + 1}、${rows1[1] + 1}行 → 这两行其他列的 $v 候选都可排除。"
                        )
                    }
                }
            }
        }
        return null
    }

    private fun DemoStep.toHint(): Hint =
        Hint(techniqueName, narration, lookCells, placement?.let { Hint.Placement(it.row, it.col, it.value) })

    // ========== Helpers ==========

    private fun posText(r: Int, c: Int) = "第${r + 1}行第${c + 1}列"

    private fun rowCells(r: Int) = (0 until 9).map { r to it }

    private fun colCells(c: Int) = (0 until 9).map { it to c }

    private fun boxCells(br: Int, bc: Int): List<Pair<Int, Int>> =
        (br * 3 until br * 3 + 3).flatMap { r -> (bc * 3 until bc * 3 + 3).map { c -> r to c } }

    private fun unitLabel(cells: List<Pair<Int, Int>>): String {
        val rows = cells.map { it.first }.toSet()
        val cols = cells.map { it.second }.toSet()
        return when {
            rows.size == 1 -> "第${rows.first() + 1}行"
            cols.size == 1 -> "第${cols.first() + 1}列"
            else -> "同一宫"
        }
    }

    private fun valuesText(mask: Int): String = valuesList(mask).joinToString("、")

    private fun valuesList(mask: Int): List<Int> = (1..9).filter { mask and (1 shl (it - 1)) != 0 }

    private fun elimOf(r: Int, c: Int, mask: Int): DemoStep.Elimination? =
        if (mask == 0) null else DemoStep.Elimination(r, c, valuesList(mask))

    // ========== Candidate Management ==========

    private fun initCandidates(board: Array<IntArray>): Array<IntArray> {
        val cands = Array(9) { IntArray(9) { ALL } }
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (board[r][c] != 0) {
                    cands[r][c] = 0
                    eliminate(cands, r, c, board[r][c])
                }
            }
        }
        return cands
    }

    private fun place(board: Array<IntArray>, cands: Array<IntArray>, r: Int, c: Int, value: Int) {
        board[r][c] = value
        cands[r][c] = 0
        eliminate(cands, r, c, value)
    }

    private fun eliminate(cands: Array<IntArray>, r: Int, c: Int, value: Int) {
        val mask = (1 shl (value - 1)).inv()
        for (i in 0 until 9) {
            cands[r][i] = cands[r][i] and mask
            cands[i][c] = cands[i][c] and mask
        }
        val br = (r / 3) * 3
        val bc = (c / 3) * 3
        for (rr in br until br + 3) {
            for (cc in bc until bc + 3) {
                cands[rr][cc] = cands[rr][cc] and mask
            }
        }
    }

    private fun isSolved(board: Array<IntArray>): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) if (board[r][c] == 0) return false
        return true
    }

    private fun allUnits(): List<List<Pair<Int, Int>>> {
        val units = mutableListOf<List<Pair<Int, Int>>>()
        for (r in 0 until 9) units.add((0 until 9).map { r to it })
        for (c in 0 until 9) units.add((0 until 9).map { it to c })
        for (br in 0 until 3) for (bc in 0 until 3) {
            val box = mutableListOf<Pair<Int, Int>>()
            for (r in br * 3 until br * 3 + 3) for (c in bc * 3 until bc * 3 + 3) box.add(r to c)
            units.add(box)
        }
        return units
    }
}
