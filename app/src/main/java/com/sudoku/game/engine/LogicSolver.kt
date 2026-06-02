package com.sudoku.game.engine

import com.sudoku.game.model.Hint

/**
 * Human-strategy Sudoku solver that applies techniques in order of difficulty.
 * Used to grade puzzle difficulty based on which techniques are required.
 *
 * Technique levels:
 * 1 - Naked Single: cell has exactly one candidate
 * 2 - Hidden Single: value has only one possible position in a unit
 * 3 - Pointing/Claiming: box-line reduction
 * 4 - Naked Pair/Triple: N cells share N candidates in a unit
 * 5 - Hidden Pair/Triple: N values appear in only N cells in a unit
 * 6 - X-Wing: rectangle pattern elimination
 */
object LogicSolver {

    data class SolveResult(val solved: Boolean, val maxLevel: Int)

    private const val ALL = 0x1FF // bits 0-8 = values 1-9

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

            val level = applyNextTechnique(copy, cands)
            if (level == -1) return SolveResult(false, maxLevel)
            maxLevel = maxOf(maxLevel, level)
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

            val level = applyNextTechnique(copy, cands)
            if (level == -1 || level > maxAllowed) return SolveResult(false, maxLevel)
            maxLevel = maxOf(maxLevel, level)
        }
    }

    // ============================================================
    // Hint generation
    //
    // Finds the next logical step a human would take from the CURRENT
    // board and explains the technique behind it. Read-only — never
    // mutates the board. The technique priority mirrors [analyze], and
    // it covers levels 1-6, so for any solvable partial of a puzzle this
    // app generates (max technique = X-Wing) a step always exists.
    // ============================================================

    /**
     * Returns the next deductive step, or null if no single-step technique
     * (levels 1-6) applies to the current board.
     */
    fun findHint(board: Array<IntArray>): Hint? {
        val cands = initCandidates(board)
        nakedSingleHint(board, cands)?.let { return it }
        hiddenSingleHint(cands)?.let { return it }
        pointingClaimingHint(cands)?.let { return it }
        nakedSubsetHint(cands)?.let { return it }
        hiddenSubsetHint(cands)?.let { return it }
        xWingHint(cands)?.let { return it }
        return null
    }

    private fun nakedSingleHint(board: Array<IntArray>, cands: Array<IntArray>): Hint? {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (board[r][c] == 0 && Integer.bitCount(cands[r][c]) == 1) {
                    val v = Integer.numberOfTrailingZeros(cands[r][c]) + 1
                    return Hint(
                        "唯一余数",
                        "${posText(r, c)}的同行、同列、同宫已经用掉了其他 8 个数字，所以这一格只能填 $v。",
                        listOf(r to c),
                        Hint.Placement(r, c, v)
                    )
                }
            }
        }
        return null
    }

    private fun hiddenSingleHint(cands: Array<IntArray>): Hint? {
        for (r in 0 until 9) {
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                var count = 0; var lastC = -1
                for (c in 0 until 9) if (cands[r][c] and bit != 0) { count++; lastC = c }
                if (count == 1) return hiddenSingleHintOf(r, lastC, v, "第${r + 1}行", rowCells(r))
            }
        }
        for (c in 0 until 9) {
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                var count = 0; var lastR = -1
                for (r in 0 until 9) if (cands[r][c] and bit != 0) { count++; lastR = r }
                if (count == 1) return hiddenSingleHintOf(lastR, c, v, "第${c + 1}列", colCells(c))
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
                    if (count == 1) return hiddenSingleHintOf(lastR, lastC, v, "第${br * 3 + bc + 1}宫", boxCells(br, bc))
                }
            }
        }
        return null
    }

    private fun hiddenSingleHintOf(r: Int, c: Int, v: Int, unitName: String, unitCells: List<Pair<Int, Int>>): Hint =
        Hint(
            "隐性唯一数",
            "在${unitName}里，数字 $v 只有${posText(r, c)}这一处能放下——其余空格都因同行/列/宫已存在 $v 而被排除。",
            unitCells,
            Hint.Placement(r, c, v)
        )

    // The four advanced detectors below are `internal` (not `private`) so each can be
    // unit-tested in isolation with a hand-built candidate grid; the public findHint()
    // always resolves singles first, which would otherwise mask them.
    internal fun pointingClaimingHint(cands: Array<IntArray>): Hint? {
        // Pointing: in a box, candidate v confined to one row/col → eliminate from rest of that line
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
                        if ((0 until 9).any { it / 3 != bc && cands[row][it] and bit != 0 }) {
                            return Hint(
                                "区块排除",
                                "第${br * 3 + bc + 1}宫中，候选数 $v 只出现在第${row + 1}行 → 第${row + 1}行其余格子都不能填 $v，可以擦掉它们的 $v 候选。",
                                cells, null
                            )
                        }
                    }
                    if (Integer.bitCount(colMask) == 1) {
                        val col = Integer.numberOfTrailingZeros(colMask)
                        if ((0 until 9).any { it / 3 != br && cands[it][col] and bit != 0 }) {
                            return Hint(
                                "区块排除",
                                "第${br * 3 + bc + 1}宫中，候选数 $v 只出现在第${col + 1}列 → 第${col + 1}列其余格子都不能填 $v。",
                                cells, null
                            )
                        }
                    }
                }
            }
        }
        // Claiming: in a row/col, candidate v confined to one box → eliminate from rest of box
        for (v in 1..9) {
            val bit = 1 shl (v - 1)
            for (r in 0 until 9) {
                var boxMask = 0
                val cells = mutableListOf<Pair<Int, Int>>()
                for (c in 0 until 9) if (cands[r][c] and bit != 0) { boxMask = boxMask or (1 shl (c / 3)); cells.add(r to c) }
                if (Integer.bitCount(boxMask) == 1) {
                    val bc = Integer.numberOfTrailingZeros(boxMask)
                    val br = r / 3
                    if ((br * 3 until br * 3 + 3).any { rr -> rr != r && (bc * 3 until bc * 3 + 3).any { cc -> cands[rr][cc] and bit != 0 } }) {
                        return Hint(
                            "区块排除",
                            "第${r + 1}行中，候选数 $v 只落在第${br * 3 + bc + 1}宫内 → 该宫其他格子都不能填 $v。",
                            cells, null
                        )
                    }
                }
            }
            for (c in 0 until 9) {
                var boxMask = 0
                val cells = mutableListOf<Pair<Int, Int>>()
                for (r in 0 until 9) if (cands[r][c] and bit != 0) { boxMask = boxMask or (1 shl (r / 3)); cells.add(r to c) }
                if (Integer.bitCount(boxMask) == 1) {
                    val br = Integer.numberOfTrailingZeros(boxMask)
                    val bc = c / 3
                    if ((bc * 3 until bc * 3 + 3).any { cc -> cc != c && (br * 3 until br * 3 + 3).any { rr -> cands[rr][cc] and bit != 0 } }) {
                        return Hint(
                            "区块排除",
                            "第${c + 1}列中，候选数 $v 只落在第${br * 3 + bc + 1}宫内 → 该宫其他格子都不能填 $v。",
                            cells, null
                        )
                    }
                }
            }
        }
        return null
    }

    internal fun nakedSubsetHint(cands: Array<IntArray>): Hint? {
        for (unit in allUnits()) {
            val empty = unit.filter { (r, c) -> cands[r][c] != 0 }
            if (empty.size < 3) continue
            for (i in empty.indices) {
                for (j in i + 1 until empty.size) {
                    val union = cands[empty[i].first][empty[i].second] or cands[empty[j].first][empty[j].second]
                    if (Integer.bitCount(union) == 2 &&
                        empty.indices.any { k -> k != i && k != j && cands[empty[k].first][empty[k].second] and union != 0 }
                    ) {
                        val pair = listOf(empty[i], empty[j])
                        return Hint(
                            "显性数对",
                            "在${unitLabel(pair)}里，这两格都只能是 ${valuesText(union)} → 同单元其他格子不能再用这两个数，可擦掉对应候选。",
                            pair, null
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
                        if (Integer.bitCount(union) == 3 &&
                            empty.indices.any { m -> m != i && m != j && m != k && cands[empty[m].first][empty[m].second] and union != 0 }
                        ) {
                            val trip = listOf(empty[i], empty[j], empty[k])
                            return Hint(
                                "显性三数组",
                                "在${unitLabel(trip)}里，这三格的候选合起来只有 ${valuesText(union)} → 它们锁定了这三个数字，同单元其他格子可排除这些候选。",
                                trip, null
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    internal fun hiddenSubsetHint(cands: Array<IntArray>): Hint? {
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
                    if (cells.any { (r, c) -> cands[r][c] and mask.inv() and ALL != 0 }) {
                        return Hint(
                            "隐性数对",
                            "在${unitLabel(cells)}里，数字 $v1 和 $v2 只可能落在这两格 → 这两格中的其他候选数都可以排除。",
                            cells, null
                        )
                    }
                }
            }
        }
        return null
    }

    internal fun xWingHint(cands: Array<IntArray>): Hint? {
        for (v in 1..9) {
            val bit = 1 shl (v - 1)
            for (r1 in 0 until 9) {
                val cols1 = (0 until 9).filter { cands[r1][it] and bit != 0 }
                if (cols1.size != 2) continue
                for (r2 in r1 + 1 until 9) {
                    val cols2 = (0 until 9).filter { cands[r2][it] and bit != 0 }
                    if (cols2 == cols1 &&
                        (0 until 9).any { r -> r != r1 && r != r2 && cols1.any { c -> cands[r][c] and bit != 0 } }
                    ) {
                        val corners = listOf(r1 to cols1[0], r1 to cols1[1], r2 to cols1[0], r2 to cols1[1])
                        return Hint(
                            "X-Wing",
                            "数字 $v 在第${r1 + 1}、${r2 + 1}行都只出现在第${cols1[0] + 1}、${cols1[1] + 1}列 → 这两列其他行的 $v 候选都可排除。",
                            corners, null
                        )
                    }
                }
            }
            for (c1 in 0 until 9) {
                val rows1 = (0 until 9).filter { cands[it][c1] and bit != 0 }
                if (rows1.size != 2) continue
                for (c2 in c1 + 1 until 9) {
                    val rows2 = (0 until 9).filter { cands[it][c2] and bit != 0 }
                    if (rows2 == rows1 &&
                        (0 until 9).any { c -> c != c1 && c != c2 && rows1.any { r -> cands[r][c] and bit != 0 } }
                    ) {
                        val corners = listOf(rows1[0] to c1, rows1[1] to c1, rows1[0] to c2, rows1[1] to c2)
                        return Hint(
                            "X-Wing",
                            "数字 $v 在第${c1 + 1}、${c2 + 1}列都只出现在第${rows1[0] + 1}、${rows1[1] + 1}行 → 这两行其他列的 $v 候选都可排除。",
                            corners, null
                        )
                    }
                }
            }
        }
        return null
    }

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

    private fun valuesText(mask: Int): String =
        (1..9).filter { mask and (1 shl (it - 1)) != 0 }.joinToString("、")

    private fun applyNextTechnique(board: Array<IntArray>, cands: Array<IntArray>): Int {
        if (nakedSingle(board, cands)) return 1
        if (hiddenSingle(board, cands)) return 2
        if (pointingClaiming(cands)) return 3
        if (nakedSubset(cands)) return 4
        if (hiddenSubset(cands)) return 5
        if (xWing(cands)) return 6
        return -1
    }

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

    // ========== Level 1: Naked Single ==========

    private fun nakedSingle(board: Array<IntArray>, cands: Array<IntArray>): Boolean {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (board[r][c] == 0 && Integer.bitCount(cands[r][c]) == 1) {
                    place(board, cands, r, c, Integer.numberOfTrailingZeros(cands[r][c]) + 1)
                    return true
                }
            }
        }
        return false
    }

    // ========== Level 2: Hidden Single ==========

    private fun hiddenSingle(board: Array<IntArray>, cands: Array<IntArray>): Boolean {
        // Rows
        for (r in 0 until 9) {
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                var count = 0; var lastC = -1
                for (c in 0 until 9) {
                    if (cands[r][c] and bit != 0) { count++; lastC = c }
                }
                if (count == 1) { place(board, cands, r, lastC, v); return true }
            }
        }
        // Columns
        for (c in 0 until 9) {
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                var count = 0; var lastR = -1
                for (r in 0 until 9) {
                    if (cands[r][c] and bit != 0) { count++; lastR = r }
                }
                if (count == 1) { place(board, cands, lastR, c, v); return true }
            }
        }
        // Boxes
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
                    if (count == 1) { place(board, cands, lastR, lastC, v); return true }
                }
            }
        }
        return false
    }

    // ========== Level 3: Pointing / Claiming ==========

    private fun pointingClaiming(cands: Array<IntArray>): Boolean {
        // Pointing: candidates in a box all in one row/col → eliminate from rest of row/col
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                for (v in 1..9) {
                    val bit = 1 shl (v - 1)
                    var rowMask = 0; var colMask = 0
                    for (r in br * 3 until br * 3 + 3) {
                        for (c in bc * 3 until bc * 3 + 3) {
                            if (cands[r][c] and bit != 0) {
                                rowMask = rowMask or (1 shl r)
                                colMask = colMask or (1 shl c)
                            }
                        }
                    }
                    if (Integer.bitCount(rowMask) == 1) {
                        val row = Integer.numberOfTrailingZeros(rowMask)
                        var changed = false
                        for (c in 0 until 9) {
                            if (c / 3 != bc && cands[row][c] and bit != 0) {
                                cands[row][c] = cands[row][c] and bit.inv(); changed = true
                            }
                        }
                        if (changed) return true
                    }
                    if (Integer.bitCount(colMask) == 1) {
                        val col = Integer.numberOfTrailingZeros(colMask)
                        var changed = false
                        for (r in 0 until 9) {
                            if (r / 3 != br && cands[r][col] and bit != 0) {
                                cands[r][col] = cands[r][col] and bit.inv(); changed = true
                            }
                        }
                        if (changed) return true
                    }
                }
            }
        }
        // Claiming: candidates in a row/col all in one box → eliminate from rest of box
        for (v in 1..9) {
            val bit = 1 shl (v - 1)
            for (r in 0 until 9) {
                var boxMask = 0
                for (c in 0 until 9) {
                    if (cands[r][c] and bit != 0) boxMask = boxMask or (1 shl (c / 3))
                }
                if (Integer.bitCount(boxMask) == 1) {
                    val bc = Integer.numberOfTrailingZeros(boxMask)
                    val br = r / 3
                    var changed = false
                    for (rr in br * 3 until br * 3 + 3) {
                        if (rr != r) {
                            for (cc in bc * 3 until bc * 3 + 3) {
                                if (cands[rr][cc] and bit != 0) {
                                    cands[rr][cc] = cands[rr][cc] and bit.inv(); changed = true
                                }
                            }
                        }
                    }
                    if (changed) return true
                }
            }
            for (c in 0 until 9) {
                var boxMask = 0
                for (r in 0 until 9) {
                    if (cands[r][c] and bit != 0) boxMask = boxMask or (1 shl (r / 3))
                }
                if (Integer.bitCount(boxMask) == 1) {
                    val br = Integer.numberOfTrailingZeros(boxMask)
                    val bc = c / 3
                    var changed = false
                    for (cc in bc * 3 until bc * 3 + 3) {
                        if (cc != c) {
                            for (rr in br * 3 until br * 3 + 3) {
                                if (cands[rr][cc] and bit != 0) {
                                    cands[rr][cc] = cands[rr][cc] and bit.inv(); changed = true
                                }
                            }
                        }
                    }
                    if (changed) return true
                }
            }
        }
        return false
    }

    // ========== Level 4: Naked Pair/Triple ==========

    private fun nakedSubset(cands: Array<IntArray>): Boolean {
        for (unit in allUnits()) {
            val empty = unit.filter { (r, c) -> cands[r][c] != 0 }
            if (empty.size < 3) continue

            // Pairs
            for (i in empty.indices) {
                for (j in i + 1 until empty.size) {
                    val union = cands[empty[i].first][empty[i].second] or
                            cands[empty[j].first][empty[j].second]
                    if (Integer.bitCount(union) == 2) {
                        var changed = false
                        for (k in empty.indices) {
                            if (k != i && k != j) {
                                val (r, c) = empty[k]
                                val before = cands[r][c]
                                cands[r][c] = cands[r][c] and union.inv()
                                if (cands[r][c] != before) changed = true
                            }
                        }
                        if (changed) return true
                    }
                }
            }

            // Triples
            for (i in empty.indices) {
                for (j in i + 1 until empty.size) {
                    for (k in j + 1 until empty.size) {
                        val union = cands[empty[i].first][empty[i].second] or
                                cands[empty[j].first][empty[j].second] or
                                cands[empty[k].first][empty[k].second]
                        if (Integer.bitCount(union) == 3) {
                            var changed = false
                            for (m in empty.indices) {
                                if (m != i && m != j && m != k) {
                                    val (r, c) = empty[m]
                                    val before = cands[r][c]
                                    cands[r][c] = cands[r][c] and union.inv()
                                    if (cands[r][c] != before) changed = true
                                }
                            }
                            if (changed) return true
                        }
                    }
                }
            }
        }
        return false
    }

    // ========== Level 5: Hidden Pair/Triple ==========

    private fun hiddenSubset(cands: Array<IntArray>): Boolean {
        for (unit in allUnits()) {
            val empty = unit.filter { (r, c) -> cands[r][c] != 0 }
            if (empty.size < 3) continue

            // For each pair of values, find which cells contain them
            for (v1 in 1..9) {
                val b1 = 1 shl (v1 - 1)
                for (v2 in v1 + 1..9) {
                    val b2 = 1 shl (v2 - 1)
                    val cells = empty.filter { (r, c) ->
                        cands[r][c] and b1 != 0 || cands[r][c] and b2 != 0
                    }
                    if (cells.size == 2) {
                        // Both values appear only in these 2 cells
                        val c1has = cells.all { (r, c) -> cands[r][c] and b1 != 0 }
                        val c2has = cells.all { (r, c) -> cands[r][c] and b2 != 0 }
                        if (!c1has || !c2has) continue

                        val mask = b1 or b2
                        var changed = false
                        for ((r, c) in cells) {
                            val before = cands[r][c]
                            cands[r][c] = cands[r][c] and mask
                            if (cands[r][c] != before) changed = true
                        }
                        if (changed) return true
                    }
                }
            }
        }
        return false
    }

    // ========== Level 6: X-Wing ==========

    private fun xWing(cands: Array<IntArray>): Boolean {
        for (v in 1..9) {
            val bit = 1 shl (v - 1)

            // Row-based: find two rows where v appears in exactly 2 cols, same cols
            for (r1 in 0 until 9) {
                val cols1 = mutableListOf<Int>()
                for (c in 0 until 9) if (cands[r1][c] and bit != 0) cols1.add(c)
                if (cols1.size != 2) continue

                for (r2 in r1 + 1 until 9) {
                    val cols2 = mutableListOf<Int>()
                    for (c in 0 until 9) if (cands[r2][c] and bit != 0) cols2.add(c)
                    if (cols2 == cols1) {
                        var changed = false
                        for (r in 0 until 9) {
                            if (r != r1 && r != r2) {
                                for (c in cols1) {
                                    if (cands[r][c] and bit != 0) {
                                        cands[r][c] = cands[r][c] and bit.inv(); changed = true
                                    }
                                }
                            }
                        }
                        if (changed) return true
                    }
                }
            }

            // Column-based
            for (c1 in 0 until 9) {
                val rows1 = mutableListOf<Int>()
                for (r in 0 until 9) if (cands[r][c1] and bit != 0) rows1.add(r)
                if (rows1.size != 2) continue

                for (c2 in c1 + 1 until 9) {
                    val rows2 = mutableListOf<Int>()
                    for (r in 0 until 9) if (cands[r][c2] and bit != 0) rows2.add(r)
                    if (rows2 == rows1) {
                        var changed = false
                        for (c in 0 until 9) {
                            if (c != c1 && c != c2) {
                                for (r in rows1) {
                                    if (cands[r][c] and bit != 0) {
                                        cands[r][c] = cands[r][c] and bit.inv(); changed = true
                                    }
                                }
                            }
                        }
                        if (changed) return true
                    }
                }
            }
        }
        return false
    }

    // ========== Helpers ==========

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
