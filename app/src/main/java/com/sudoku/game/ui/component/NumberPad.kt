package com.sudoku.game.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumberPad(
    onNumberClick: (Int) -> Unit,
    onClear: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNoteToggle: () -> Unit,
    onHint: () -> Unit,
    isNoteMode: Boolean,
    numberCounts: Map<Int, Int>,
    hintsRemaining: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(text = "撤销", onClick = onUndo, enabled = canUndo)
            ActionButton(text = "重做", onClick = onRedo, enabled = canRedo)
            ActionButton(
                text = if (isNoteMode) "笔记 ON" else "笔记",
                onClick = onNoteToggle,
                isActive = isNoteMode
            )
            ActionButton(text = "清除", onClick = onClear)
            ActionButton(text = "提示($hintsRemaining)", onClick = onHint, enabled = hintsRemaining > 0)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (num in 1..9) {
                val count = numberCounts.getOrDefault(num, 0)
                NumberButton(
                    number = num,
                    onClick = { onNumberClick(num) },
                    isComplete = count >= 9,
                    isNoteMode = isNoteMode
                )
            }
        }
    }
}

@Composable
private fun NumberButton(
    number: Int,
    onClick: () -> Unit,
    isComplete: Boolean,
    isNoteMode: Boolean
) {
    Button(
        onClick = onClick,
        enabled = !isComplete,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isNoteMode)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isNoteMode)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(
            text = number.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                Color.Transparent
        )
    ) {
        Text(text = text, fontSize = 12.sp)
    }
}
