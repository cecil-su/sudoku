package com.sudoku.game.engine

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
