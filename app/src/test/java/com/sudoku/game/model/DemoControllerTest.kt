package com.sudoku.game.model

import org.junit.Assert.*
import org.junit.Test

class DemoControllerTest {

    private fun step(
        level: Int = 1,
        look: List<Pair<Int, Int>> = listOf(0 to 0),
        elim: List<DemoStep.Elimination> = emptyList(),
        placement: DemoStep.Placement? = null
    ) = DemoStep(level, "T$level", look, elim, placement, "narration $level")

    private fun controller(n: Int) = DemoController(List(n) { step(level = it + 1) })

    @Test
    fun `empty timeline is inert`() {
        val c = DemoController(emptyList())
        assertNull(c.current)
        assertEquals(0, c.totalSteps)
        assertEquals(0, c.stepNumber)
        assertFalse(c.hasNext)
        assertFalse(c.hasPrev)
        assertSame(c, c.next())
        assertSame(c, c.prev())
        assertSame(c, c.replay())
        assertSame(c, c.jumpTo(3))
    }

    @Test
    fun `single step is both start and end`() {
        val c = controller(1)
        assertEquals(1, c.stepNumber)
        assertTrue(c.isAtStart)
        assertTrue(c.isAtEnd)
        assertFalse(c.hasNext)
        assertFalse(c.hasPrev)
        assertSame(c, c.next())
        assertSame(c, c.prev())
    }

    @Test
    fun `next advances and clamps at the end`() {
        var c = controller(3)
        assertEquals(1, c.stepNumber)
        c = c.next()
        assertEquals(2, c.stepNumber)
        c = c.next()
        assertEquals(3, c.stepNumber)
        assertTrue(c.isAtEnd)
        assertSame(c, c.next()) // clamps, no-op
    }

    @Test
    fun `prev steps back to zero and clamps`() {
        var c = controller(3).jumpTo(2)
        assertEquals(3, c.stepNumber)
        c = c.prev()
        assertEquals(2, c.stepNumber)
        c = c.prev()
        assertEquals(1, c.stepNumber)
        assertTrue(c.isAtStart)
        assertSame(c, c.prev()) // clamps, no-op
    }

    @Test
    fun `replay resets to the first step`() {
        val c = controller(5).jumpTo(4)
        assertEquals(5, c.stepNumber)
        val r = c.replay()
        assertEquals(1, r.stepNumber)
        assertEquals(0, r.currentIndex)
    }

    @Test
    fun `jumpTo clamps out-of-range indices`() {
        val c = controller(4)
        assertEquals(0, c.jumpTo(-5).currentIndex)
        assertEquals(3, c.jumpTo(99).currentIndex)
        assertEquals(2, c.jumpTo(2).currentIndex)
    }

    @Test
    fun `gotoCell finds a step by look, elimination, or placement`() {
        val steps = listOf(
            step(level = 1, look = listOf(0 to 0)),
            step(level = 3, look = listOf(1 to 1), elim = listOf(DemoStep.Elimination(4, 5, listOf(7)))),
            step(level = 1, look = listOf(2 to 2), placement = DemoStep.Placement(8, 8, 9))
        )
        val c = DemoController(steps)
        assertEquals(0, c.gotoCell(0, 0).currentIndex)       // look cell
        assertEquals(1, c.gotoCell(4, 5).currentIndex)       // eliminated cell
        assertEquals(2, c.gotoCell(8, 8).currentIndex)       // placement cell
        assertSame(c, c.gotoCell(7, 3))                      // untouched cell → no-op
    }
}
