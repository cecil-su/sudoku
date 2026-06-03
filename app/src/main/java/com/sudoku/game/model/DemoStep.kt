package com.sudoku.game.model

/**
 * One deductive step on the demo timeline. Produced by
 * [com.sudoku.game.engine.LogicSolver.demoTrajectory] and by the shared kernel
 * that also backs [com.sudoku.game.engine.LogicSolver.findHint].
 *
 * A step is the atomic unit the on-board demo player walks through. The frames a
 * learner sees, in order:
 *  - [lookCells]: the pattern — which cells to look at.
 *  - [eliminatedCells]: the candidates this technique strikes (empty for singles).
 *  - [placement]: the conclusion to fill (null for pure-elimination steps).
 *
 * [eliminatedCells] is the demo's most important frame and the reason a plain
 * [Hint] is not enough: elimination techniques (pointing/claiming, naked/hidden
 * subset, X-Wing) conclude by *removing candidates*, not by filling a cell, and
 * the demo must show exactly which candidates disappear.
 *
 * [level] is the technique tier (1 = Naked Single … 6 = X-Wing); it lets the
 * difficulty analysis and the trajectory share one detection kernel.
 */
data class DemoStep(
    val level: Int,
    val techniqueName: String,
    val lookCells: List<Pair<Int, Int>>,
    val eliminatedCells: List<Elimination>,
    val placement: Placement?,
    val narration: String
) {
    /** Candidate digits [values] struck from cell ([row], [col]) by this step. */
    data class Elimination(val row: Int, val col: Int, val values: List<Int>)

    /** The single value this step concludes can be placed at ([row], [col]). */
    data class Placement(val row: Int, val col: Int, val value: Int)
}
