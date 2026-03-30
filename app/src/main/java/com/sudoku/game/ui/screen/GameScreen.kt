package com.sudoku.game.ui.screen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sudoku.game.engine.SudokuValidator
import com.sudoku.game.model.GameState
import com.sudoku.game.ui.component.GameTopBar
import com.sudoku.game.ui.component.NumberPad
import com.sudoku.game.ui.component.SudokuBoard
import com.sudoku.game.viewmodel.GameViewModel

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val gameState by viewModel.state.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()

    // Pause/resume timer with lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.pauseTimer()
                Lifecycle.Event.ON_RESUME -> viewModel.resumeTimer()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isGenerating) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("生成谜题中...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val state = gameState ?: return

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GameTopBar(
                difficultyLabel = state.difficulty.label,
                elapsedSeconds = state.elapsedSeconds,
                onBack = {
                    viewModel.pauseTimer()
                    onBack()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SudokuBoard(
                gameState = state,
                onCellClick = { row, col -> viewModel.selectCell(row, col) },
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.weight(1f))

            if (state.isCompleted) {
                CompletionBanner(elapsedSeconds = state.elapsedSeconds)
            } else {
                val numberCounts = (1..9).associateWith { num ->
                    SudokuValidator.countValue(state.cells, num)
                }

                NumberPad(
                    onNumberClick = { viewModel.inputNumber(it) },
                    onClear = { viewModel.clearCell() },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onNoteToggle = { viewModel.toggleNoteMode() },
                    onHint = { viewModel.useHint() },
                    isNoteMode = state.isNoteMode,
                    numberCounts = numberCounts,
                    hintsRemaining = GameState.MAX_HINTS - state.hintsUsed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CompletionBanner(elapsedSeconds: Long) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "恭喜完成！",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            Text(
                text = "用时 %02d:%02d".format(mins, secs),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
