package com.sudoku.game.model

/**
 * An active "your turn" challenge: the demo hid the conclusion at ([row], [col])
 * — whose correct value is [answer], justified by [technique] — for the player to
 * fill in themselves. [result] is null while awaiting the guess.
 */
data class DemoChallenge(
    val row: Int,
    val col: Int,
    val answer: Int,
    val technique: String,
    val result: Result? = null
) {
    enum class Result { CORRECT, WRONG }
}
