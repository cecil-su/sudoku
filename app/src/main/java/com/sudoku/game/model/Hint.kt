package com.sudoku.game.model

/**
 * A teaching hint: the next logical step the player can take from the current
 * board, together with the technique that justifies it and a human explanation.
 * Produced by [com.sudoku.game.engine.LogicSolver.findHint].
 *
 * [placement] is non-null when the step is directly fillable (a single); for
 * candidate-elimination techniques it is null and the hint only teaches the
 * pattern via [explanation] and [highlightCells].
 */
data class Hint(
    val techniqueName: String,
    val explanation: String,
    val highlightCells: List<Pair<Int, Int>>,
    val placement: Placement? = null
) {
    data class Placement(val row: Int, val col: Int, val value: Int)
}
