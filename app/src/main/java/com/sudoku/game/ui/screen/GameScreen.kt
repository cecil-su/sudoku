package com.sudoku.game.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sudoku.game.model.DemoController
import com.sudoku.game.model.GameState
import com.sudoku.game.model.Hint
import com.sudoku.game.ui.component.DemoHighlight
import com.sudoku.game.ui.component.DemoPlayer
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
    val activeHint by viewModel.activeHint.collectAsState()
    val demo by viewModel.demo.collectAsState()
    val demoChallenge by viewModel.demoChallenge.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val coachReply by viewModel.coachReply.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.pauseTimer()
                Lifecycle.Event.ON_RESUME -> viewModel.resumeTimer()
                Lifecycle.Event.ON_STOP -> viewModel.stopAiWork()
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
            val activeDemo = demo
            val demoStep = activeDemo?.current

            GameTopBar(
                difficultyLabel = state.difficulty.label,
                elapsedSeconds = elapsedSeconds,
                onBack = {
                    viewModel.exitGame()
                    onBack()
                },
                onDemo = if (!state.isCompleted && activeDemo == null) {
                    { viewModel.startDemo() }
                } else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (activeDemo != null && demoStep != null) {
                val challenge = demoChallenge
                val awaiting = challenge != null && challenge.result == null
                val blankCell = if (awaiting) challenge!!.row to challenge.col else null
                val demoBoard = remember(state, activeDemo, demoChallenge) {
                    buildDemoBoard(state, activeDemo, blankCell)
                }
                SudokuBoard(
                    gameState = demoBoard,
                    onCellClick = { _, _ -> },
                    isDarkTheme = isDarkTheme,
                    demoHighlight = DemoHighlight(step = demoStep, challengeCell = blankCell)
                )

                Spacer(modifier = Modifier.weight(1f))

                DemoPlayer(
                    controller = activeDemo,
                    challenge = demoChallenge,
                    onNext = { viewModel.demoNext() },
                    onPrev = { viewModel.demoPrev() },
                    onReplay = { viewModel.demoReplay() },
                    onExit = { viewModel.exitDemo() },
                    onTry = { viewModel.startChallenge() },
                    onSubmit = { viewModel.submitChallenge(it) },
                    onCancelChallenge = { viewModel.dismissChallenge() },
                    aiAvailable = activeProvider != null,
                    aiBusy = aiBusy,
                    coachReply = coachReply,
                    onAsk = { viewModel.askCoach(it) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                SudokuBoard(
                    gameState = state,
                    onCellClick = { row, col -> viewModel.selectCell(row, col) },
                    isDarkTheme = isDarkTheme,
                    hintCells = activeHint?.highlightCells?.toSet() ?: emptySet()
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
                    activeHint?.let { hint ->
                        HintCard(
                            hint = hint,
                            onApply = { viewModel.applyHint() },
                            onDismiss = { viewModel.dismissHint() }
                        )
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
                        canUndo = canUndo,
                        canRedo = canRedo,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Builds the read-only board shown during a demo: the original puzzle with every
 * placement up to and including the current step applied (so cells fill as you
 * step forward, empty as you step back). Notes/errors are cleared for focus. When
 * [blankCell] is set (a "your turn" challenge), that cell is left empty to guess.
 * This is derived for display only — it never touches the real [GameState].
 */
private fun buildDemoBoard(
    state: GameState,
    controller: DemoController,
    blankCell: Pair<Int, Int>?
): GameState {
    val cells = state.cells.map { row ->
        row.map { it.copy(notes = emptySet(), isError = false) }.toMutableList()
    }.toMutableList()
    for (i in 0..controller.currentIndex) {
        val p = controller.steps[i].placement ?: continue
        cells[p.row][p.col] = cells[p.row][p.col].copy(value = p.value, isGiven = false)
    }
    blankCell?.let { (r, c) -> cells[r][c] = cells[r][c].copy(value = 0) }
    return state.copy(
        cells = cells.map { it.toList() },
        selectedRow = -1,
        selectedCol = -1
    )
}

@Composable
private fun HintCard(
    hint: Hint,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = hint.techniqueName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("知道了") }
                if (hint.placement != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onApply) { Text("填入") }
                }
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
