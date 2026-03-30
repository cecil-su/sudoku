package com.sudoku.game.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudoku.game.model.GameState
import com.sudoku.game.ui.theme.*

@Composable
fun SudokuBoard(
    gameState: GameState,
    onCellClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()

    val gridColor = if (isDarkTheme) Color(0xFF90A4AE) else Color(0xFF37474F)
    val thinLineColor = if (isDarkTheme) Color(0xFF546E7A) else Color(0xFFB0BEC5)
    val bgColor = if (isDarkTheme) Color(0xFF1A1A2E) else Color.White

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cellSize = size.width / 9f
                    val col = (offset.x / cellSize).toInt().coerceIn(0, 8)
                    val row = (offset.y / cellSize).toInt().coerceIn(0, 8)
                    onCellClick(row, col)
                }
            }
    ) {
        val cellSize = size.width / 9f

        // Draw background
        drawRect(bgColor)

        // Draw cell backgrounds (highlights)
        drawCellBackgrounds(gameState, cellSize, isDarkTheme)

        // Draw numbers and notes
        drawNumbers(gameState, cellSize, textMeasurer, isDarkTheme)

        // Draw grid lines
        drawGridLines(cellSize, gridColor, thinLineColor)
    }
}

private fun DrawScope.drawCellBackgrounds(
    gameState: GameState,
    cellSize: Float,
    isDarkTheme: Boolean
) {
    val selectedBg = if (isDarkTheme) Color(0xFF1E3A5F) else SelectedCell
    val peerBg = if (isDarkTheme) Color(0xFF162447) else HighlightRowColBox
    val sameNumBg = if (isDarkTheme) Color(0xFF1B3B5A) else HighlightSameNumber
    val errorBg = if (isDarkTheme) Color(0xFF4A1A1A) else Color(0xFFFFCDD2)

    for (r in 0 until 9) {
        for (c in 0 until 9) {
            val cell = gameState.getCell(r, c)
            val topLeft = Offset(c * cellSize, r * cellSize)
            val cellSizeObj = Size(cellSize, cellSize)

            when {
                gameState.isSelected(r, c) -> drawRect(selectedBg, topLeft, cellSizeObj)
                cell.isError -> drawRect(errorBg, topLeft, cellSizeObj)
                gameState.isSameValue(r, c) -> drawRect(sameNumBg, topLeft, cellSizeObj)
                gameState.isSameRowColBox(r, c) -> drawRect(peerBg, topLeft, cellSizeObj)
            }
        }
    }
}

private fun DrawScope.drawNumbers(
    gameState: GameState,
    cellSize: Float,
    textMeasurer: TextMeasurer,
    isDarkTheme: Boolean
) {
    val givenColor = if (isDarkTheme) Color(0xFFE0E0E0) else PresetNumberColor
    val playerColor = if (isDarkTheme) Color(0xFF64B5F6) else PlayerNumberColor
    val errorTextColor = ErrorColor
    val noteColor = if (isDarkTheme) Color(0xFF78909C) else NoteColor

    val numberSize = (cellSize * 0.55f).sp
    val noteSize = (cellSize * 0.22f).sp

    for (r in 0 until 9) {
        for (c in 0 until 9) {
            val cell = gameState.getCell(r, c)

            if (cell.value != 0) {
                val color = when {
                    cell.isGiven -> givenColor
                    cell.isError -> errorTextColor
                    else -> playerColor
                }
                val style = TextStyle(
                    color = color,
                    fontSize = numberSize,
                    fontWeight = if (cell.isGiven) FontWeight.Bold else FontWeight.Medium
                )
                val text = cell.value.toString()
                val measured = textMeasurer.measure(text, style)
                val x = c * cellSize + (cellSize - measured.size.width) / 2
                val y = r * cellSize + (cellSize - measured.size.height) / 2
                drawText(measured, topLeft = Offset(x, y))
            } else if (cell.notes.isNotEmpty()) {
                // Draw notes in 3x3 grid within cell
                val noteStyle = TextStyle(color = noteColor, fontSize = noteSize)
                for (num in cell.notes) {
                    val noteRow = (num - 1) / 3
                    val noteCol = (num - 1) % 3
                    val text = num.toString()
                    val measured = textMeasurer.measure(text, noteStyle)
                    val subCellW = cellSize / 3
                    val subCellH = cellSize / 3
                    val x = c * cellSize + noteCol * subCellW + (subCellW - measured.size.width) / 2
                    val y = r * cellSize + noteRow * subCellH + (subCellH - measured.size.height) / 2
                    drawText(measured, topLeft = Offset(x, y))
                }
            }
        }
    }
}

private fun DrawScope.drawGridLines(
    cellSize: Float,
    thickColor: Color,
    thinColor: Color
) {
    val thickWidth = 3f
    val thinWidth = 1f

    for (i in 0..9) {
        val pos = i * cellSize
        val isThick = i % 3 == 0
        val color = if (isThick) thickColor else thinColor
        val width = if (isThick) thickWidth else thinWidth

        // Horizontal
        drawLine(color, Offset(0f, pos), Offset(size.width, pos), strokeWidth = width)
        // Vertical
        drawLine(color, Offset(pos, 0f), Offset(pos, size.height), strokeWidth = width)
    }
}
