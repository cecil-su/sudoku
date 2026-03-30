package com.sudoku.game.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
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
    val currentOnCellClick = rememberUpdatedState(onCellClick)

    val gridColor = if (isDarkTheme) GridColorDark else GridColorLight
    val thinLineColor = if (isDarkTheme) ThinLineColorDark else ThinLineColorLight
    val bgColor = if (isDarkTheme) BoardBgDark else Color.White

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
                    currentOnCellClick.value(row, col)
                }
            }
    ) {
        val cellSize = size.width / 9f

        drawRect(bgColor)
        drawCellBackgrounds(gameState, cellSize, isDarkTheme)
        drawNumbers(gameState, cellSize, textMeasurer, isDarkTheme)
        drawGridLines(cellSize, gridColor, thinLineColor)
    }
}

private fun DrawScope.drawCellBackgrounds(
    gameState: GameState,
    cellSize: Float,
    isDarkTheme: Boolean
) {
    val selectedBg = if (isDarkTheme) SelectedCellDark else SelectedCell
    val peerBg = if (isDarkTheme) HighlightRowColBoxDark else HighlightRowColBox
    val sameNumBg = if (isDarkTheme) HighlightSameNumberDark else HighlightSameNumber
    val errorBg = if (isDarkTheme) ErrorCellBgDark else ErrorCellBg

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
    val givenColor = if (isDarkTheme) PresetNumberColorDark else PresetNumberColor
    val playerColor = if (isDarkTheme) PlayerNumberColorDark else PlayerNumberColor
    val errorTextColor = ErrorColor
    val noteColor = if (isDarkTheme) NoteColorDark else NoteColor

    // Convert px to sp: divide by density*fontScale so text stays proportional to cell
    val pxToSp = 1f / (density * fontScale)
    val numberSize = (cellSize * 0.40f * pxToSp).sp
    val noteSize = (cellSize * 0.18f * pxToSp).sp

    // Cache text measurements — digits 1-9 have fixed sizes at a given font size
    val givenMeasured = measureDigits(textMeasurer, numberSize, givenColor, FontWeight.Bold)
    val playerMeasured = measureDigits(textMeasurer, numberSize, playerColor, FontWeight.Medium)
    val errorMeasured = measureDigits(textMeasurer, numberSize, errorTextColor, FontWeight.Medium)
    val noteMeasured = measureDigits(textMeasurer, noteSize, noteColor, FontWeight.Normal)

    for (r in 0 until 9) {
        for (c in 0 until 9) {
            val cell = gameState.getCell(r, c)

            if (cell.value != 0) {
                val measured = when {
                    cell.isGiven -> givenMeasured[cell.value - 1]
                    cell.isError -> errorMeasured[cell.value - 1]
                    else -> playerMeasured[cell.value - 1]
                }
                val x = c * cellSize + (cellSize - measured.size.width) / 2
                val y = r * cellSize + (cellSize - measured.size.height) / 2
                drawText(measured, topLeft = Offset(x, y))
            } else if (cell.notes.isNotEmpty()) {
                val subCellW = cellSize / 3
                val subCellH = cellSize / 3
                for (num in cell.notes) {
                    val noteRow = (num - 1) / 3
                    val noteCol = (num - 1) % 3
                    val measured = noteMeasured[num - 1]
                    val x = c * cellSize + noteCol * subCellW + (subCellW - measured.size.width) / 2
                    val y = r * cellSize + noteRow * subCellH + (subCellH - measured.size.height) / 2
                    drawText(measured, topLeft = Offset(x, y))
                }
            }
        }
    }
}

private fun measureDigits(
    textMeasurer: TextMeasurer,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight
): List<TextLayoutResult> {
    val style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight)
    return (1..9).map { textMeasurer.measure(it.toString(), style) }
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

        drawLine(color, Offset(0f, pos), Offset(size.width, pos), strokeWidth = width)
        drawLine(color, Offset(pos, 0f), Offset(pos, size.height), strokeWidth = width)
    }
}
