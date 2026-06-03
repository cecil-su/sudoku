package com.sudoku.game.ai

import com.sudoku.game.model.DemoController
import com.sudoku.game.model.DemoStep
import org.junit.Assert.*
import org.junit.Test

class AiCoachToolTest {

    private fun step(tech: String, row: Int, col: Int, value: Int) = DemoStep(
        level = 1,
        techniqueName = tech,
        lookCells = listOf(row to col),
        eliminatedCells = emptyList(),
        placement = DemoStep.Placement(row, col, value),
        narration = tech
    )

    private val ctrl = DemoController(
        steps = listOf(step("A", 0, 0, 1), step("B", 1, 1, 2), step("C", 2, 2, 3)),
        currentIndex = 0
    )

    @Test fun `next advances`() =
        assertEquals(1, applyCoachTool(ctrl, "next", null, null, null).currentIndex)

    @Test fun `prev from start is no-op`() =
        assertEquals(0, applyCoachTool(ctrl, "prev", null, null, null).currentIndex)

    @Test fun `replay returns to start`() =
        assertEquals(0, applyCoachTool(ctrl.jumpTo(2), "replay", null, null, null).currentIndex)

    @Test fun `jumpTo is 1-based`() {
        assertEquals(2, applyCoachTool(ctrl, "jumpTo", 3, null, null).currentIndex)
        assertEquals(0, applyCoachTool(ctrl, "jumpTo", 1, null, null).currentIndex)
    }

    @Test fun `jumpTo clamps out-of-range`() =
        assertEquals(2, applyCoachTool(ctrl, "jumpTo", 99, null, null).currentIndex)

    @Test fun `jumpTo without index is no-op`() =
        assertEquals(0, applyCoachTool(ctrl, "jumpTo", null, null, null).currentIndex)

    @Test fun `gotoCell is 1-based and finds the step`() =
        assertEquals(2, applyCoachTool(ctrl, "gotoCell", null, 3, 3).currentIndex)

    @Test fun `gotoCell unknown cell is no-op`() =
        assertEquals(0, applyCoachTool(ctrl, "gotoCell", null, 9, 9).currentIndex)

    @Test fun `unknown tool is no-op`() =
        assertEquals(0, applyCoachTool(ctrl, "bogus", 5, 5, 5).currentIndex)

    @Test fun `conclusion renders placement 1-based`() =
        assertEquals("在 R1C5 填 7", conclusion(step("A", 0, 4, 7)))

    @Test fun `conclusion renders eliminations`() {
        val s = DemoStep(3, "X", listOf(0 to 0), listOf(DemoStep.Elimination(1, 2, listOf(4, 5))), null, "x")
        assertEquals("排除候选：R2C3 去掉 4、5", conclusion(s))
    }
}
