package com.sudoku.game.model

/**
 * Immutable navigation state over a demo [steps] timeline. Every operation is
 * pure — it returns a new controller and never mutates. This is the single
 * source of truth for which [DemoStep] the player is currently looking at; the
 * offline buttons drive it directly, and (10.4) the AI voice controller will map
 * spoken intents onto the same moves.
 */
data class DemoController(
    val steps: List<DemoStep>,
    val currentIndex: Int = 0
) {
    val current: DemoStep? get() = steps.getOrNull(currentIndex)

    val totalSteps: Int get() = steps.size

    /** 1-based position for display ("3 / 12"); 0 when there are no steps. */
    val stepNumber: Int get() = if (steps.isEmpty()) 0 else currentIndex + 1

    val hasPrev: Boolean get() = currentIndex > 0

    val hasNext: Boolean get() = currentIndex < steps.lastIndex

    val isAtStart: Boolean get() = currentIndex == 0

    val isAtEnd: Boolean get() = currentIndex >= steps.lastIndex

    fun next(): DemoController = if (hasNext) copy(currentIndex = currentIndex + 1) else this

    fun prev(): DemoController = if (hasPrev) copy(currentIndex = currentIndex - 1) else this

    fun replay(): DemoController = if (currentIndex == 0) this else copy(currentIndex = 0)

    fun jumpTo(index: Int): DemoController {
        if (steps.isEmpty()) return this
        val clamped = index.coerceIn(0, steps.lastIndex)
        return if (clamped == currentIndex) this else copy(currentIndex = clamped)
    }

    /**
     * Jumps to the first step that references cell ([row], [col]) — as a look
     * cell, an eliminated cell, or its placement. No-op if no step touches it.
     * The offline board stays read-only; this exists for the AI controller's
     * "explain this cell" (10.4).
     */
    fun gotoCell(row: Int, col: Int): DemoController {
        val idx = steps.indexOfFirst { step ->
            (row to col) in step.lookCells ||
                step.eliminatedCells.any { it.row == row && it.col == col } ||
                (step.placement?.let { it.row == row && it.col == col } ?: false)
        }
        return if (idx < 0 || idx == currentIndex) this else copy(currentIndex = idx)
    }
}
