package com.sudoku.game.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val numberCounts by viewModel.numberCounts.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()

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
                elapsedSeconds = elapsedSeconds,
                onBack = {
                    viewModel.exitGame()
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
                CompletionBanner(
                    elapsedSeconds = elapsedSeconds,
                    onBackToMenu = {
                        viewModel.exitGame()
                        onBack()
                    }
                )
            } else {
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
                    canUndo = canUndo,
                    canRedo = canRedo,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CompletionBanner(
    elapsedSeconds: Long,
    onBackToMenu: () -> Unit
) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(400)) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale.value).alpha(alpha.value)
        ) {
            Text(text = "🎉", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "恭喜完成！",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "用时 %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBackToMenu) {
                Text("返回主页", fontSize = 16.sp)
            }
        }
    }
}
