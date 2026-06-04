package com.sudoku.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sudoku.game.ai.AiCoach
import com.sudoku.game.ai.ChatMessage
import com.sudoku.game.data.GameRepository
import com.sudoku.game.data.ProviderRepository
import com.sudoku.game.data.StatsRepository
import com.sudoku.game.engine.LogicSolver
import com.sudoku.game.engine.SudokuGenerator
import com.sudoku.game.engine.SudokuValidator
import com.sudoku.game.model.AiProvider
import com.sudoku.game.model.Cell
import com.sudoku.game.model.DemoChallenge
import com.sudoku.game.model.DemoController
import com.sudoku.game.model.Difficulty
import com.sudoku.game.model.GameState
import com.sudoku.game.model.Hint
import com.sudoku.game.model.SessionTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepo = GameRepository(application)
    private val statsRepo = StatsRepository(application)
    private val providerRepo = ProviderRepository(application)
    private val coach = AiCoach()
    private val saveMutex = Mutex()
    private var aiHistory = emptyList<ChatMessage>()
    // AI work runs here, NOT in viewModelScope: it must cancel when the screen stops
    // (resource-lifecycle rule), not only when the VM is cleared. Recreated after cancel.
    private var aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    // Separated from GameState to avoid full board recomposition every second
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _hasSavedGame = MutableStateFlow(false)
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

    private val _numberCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val numberCounts: StateFlow<Map<Int, Int>> = _numberCounts.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _activeHint = MutableStateFlow<Hint?>(null)
    val activeHint: StateFlow<Hint?> = _activeHint.asStateFlow()

    private val _demo = MutableStateFlow<DemoController?>(null)
    val demo: StateFlow<DemoController?> = _demo.asStateFlow()

    private val _demoChallenge = MutableStateFlow<DemoChallenge?>(null)
    val demoChallenge: StateFlow<DemoChallenge?> = _demoChallenge.asStateFlow()

    // Local-only learning telemetry — never serialized into GameState, never sent anywhere.
    private val _telemetry = MutableStateFlow(SessionTelemetry())
    val telemetry: StateFlow<SessionTelemetry> = _telemetry.asStateFlow()

    val activeProvider: StateFlow<AiProvider?> = providerRepo.settings
        .map { it.activeProvider }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    // Latest AI coach reply to show/speak in the demo panel (cleared on manual nav).
    private val _coachReply = MutableStateFlow<String?>(null)
    val coachReply: StateFlow<String?> = _coachReply.asStateFlow()

    val stats = statsRepo.getStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.sudoku.game.model.GameStats())

    private var timerJob: Job? = null
    private var autoSaveJob: Job? = null

    private val stateMutex = Mutex()
    private val undoStack = mutableListOf<List<List<Cell>>>()
    private val redoStack = mutableListOf<List<List<Cell>>>()

    init {
        viewModelScope.launch {
            _hasSavedGame.value = gameRepo.hasSavedGame()
        }
    }

    fun newGame(difficulty: Difficulty) {
        stopAllJobs()
        _activeHint.value = null
        clearDemo()
        _telemetry.value = SessionTelemetry()
        viewModelScope.launch {
            stateMutex.withLock {
                undoStack.clear()
                redoStack.clear()
                updateStackStates()
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            _isGenerating.value = true
            val gameState = SudokuGenerator.generate(difficulty)
            _state.value = gameState
            _elapsedSeconds.value = 0
            _isGenerating.value = false
            recomputeNumberCounts(gameState)
            statsRepo.recordGameStarted()
            startTimer()
            startAutoSave()
        }
    }

    fun continueGame() {
        _activeHint.value = null
        clearDemo()
        _telemetry.value = SessionTelemetry()
        viewModelScope.launch {
            val saved = gameRepo.loadGame()
            if (saved != null && !saved.isCompleted) {
                stopAllJobs()
                _state.value = saved
                _elapsedSeconds.value = saved.restoredElapsedSeconds
                stateMutex.withLock {
                    undoStack.clear()
                    redoStack.clear()
                    updateStackStates()
                }
                recomputeNumberCounts(saved)
                startTimer()
                startAutoSave()
            }
        }
    }

    fun exitGame() {
        val current = _state.value
        val elapsed = _elapsedSeconds.value
        stopAllJobs()
        clearDemo()
        _state.value = null
        if (current != null && !current.isCompleted) {
            viewModelScope.launch(Dispatchers.IO) {
                saveMutex.withLock {
                    gameRepo.saveGame(current, elapsed)
                    _hasSavedGame.value = true
                }
            }
        }
    }

    fun selectCell(row: Int, col: Int) {
        if (row !in 0..8 || col !in 0..8) return
        _activeHint.value = null
        _state.value = _state.value?.copy(selectedRow = row, selectedCol = col)
    }

    fun inputNumber(number: Int) {
        if (number !in 1..9) return
        _activeHint.value = null
        viewModelScope.launch {
            stateMutex.withLock {
                val current = _state.value ?: return@withLock
                if (current.isCompleted || !current.hasSelection) return@withLock
                val cell = current.getCell(current.selectedRow, current.selectedCol)
                if (cell.isGiven) return@withLock

                pushUndo(current.cells)

                val newState = if (current.isNoteMode) {
                    applyNote(current, current.selectedRow, current.selectedCol, number)
                } else {
                    applyValue(current, current.selectedRow, current.selectedCol, number)
                }
                _state.value = newState
            }
        }
    }

    private fun applyValue(current: GameState, row: Int, col: Int, number: Int): GameState {
        val oldValue = current.getCell(row, col).value
        val newValue = if (oldValue == number) 0 else number
        return commitValue(current, row, col, oldValue, newValue)
    }

    /**
     * Writes [newValue] into ([row], [col]) (0 clears it), clears peer notes, then
     * recomputes errors, completion and number counts. Shared by manual input and
     * hint fills — it does not toggle and does not change the selection.
     */
    private fun commitValue(current: GameState, row: Int, col: Int, oldValue: Int, newValue: Int): GameState {
        if (newValue in 1..9 && newValue != current.solution[row][col]) {
            _telemetry.value = _telemetry.value.recordError()
        }
        val newCells = current.cells.map { it.toMutableList() }.toMutableList()
        newCells[row][col] = newCells[row][col].copy(value = newValue, notes = emptySet())

        if (newValue != 0) {
            removeNotesFromPeers(newCells, row, col, newValue)
        }

        val updatedCells = updateErrors(newCells)
        val cellsList = updatedCells.map { it.toList() }
        val isCompleted = SudokuValidator.isComplete(cellsList)

        if (isCompleted) {
            timerJob?.cancel()
            onGameCompleted(current.difficulty, _elapsedSeconds.value)
        }

        updateNumberCountsIncremental(oldValue, newValue)
        return current.copy(cells = cellsList, isCompleted = isCompleted)
    }

    private fun applyNote(current: GameState, row: Int, col: Int, number: Int): GameState {
        val cell = current.getCell(row, col)
        if (cell.value != 0) return current

        val newNotes = if (number in cell.notes) cell.notes - number else cell.notes + number
        val newCells = current.cells.map { it.toMutableList() }.toMutableList()
        newCells[row][col] = cell.copy(notes = newNotes)
        return current.copy(cells = newCells.map { it.toList() })
    }

    fun clearCell() {
        _activeHint.value = null
        viewModelScope.launch {
            stateMutex.withLock {
                val current = _state.value ?: return@withLock
                if (current.isCompleted || !current.hasSelection) return@withLock
                val cell = current.getCell(current.selectedRow, current.selectedCol)
                if (cell.isGiven) return@withLock

                pushUndo(current.cells)

                val oldValue = cell.value
                val newCells = current.cells.map { it.toMutableList() }.toMutableList()
                newCells[current.selectedRow][current.selectedCol] = cell.copy(value = 0, notes = emptySet())
                val updatedCells = updateErrors(newCells)
                _state.value = current.copy(cells = updatedCells.map { it.toList() })
                if (oldValue != 0) updateNumberCountsIncremental(oldValue, 0)
            }
        }
    }

    fun toggleNoteMode() {
        _state.value = _state.value?.let { it.copy(isNoteMode = !it.isNoteMode) }
    }

    fun undo() {
        _activeHint.value = null
        viewModelScope.launch {
            stateMutex.withLock {
                if (undoStack.isEmpty()) return@withLock
                val current = _state.value ?: return@withLock
                redoStack.add(current.cells)
                if (redoStack.size > MAX_STACK_SIZE) redoStack.removeAt(0)
                val previous = undoStack.removeAt(undoStack.lastIndex)
                val newState = current.copy(cells = previous)
                recomputeNumberCounts(newState)
                _state.value = newState
                updateStackStates()
            }
        }
    }

    fun redo() {
        _activeHint.value = null
        viewModelScope.launch {
            stateMutex.withLock {
                if (redoStack.isEmpty()) return@withLock
                val current = _state.value ?: return@withLock
                undoStack.add(current.cells)
                if (undoStack.size > MAX_STACK_SIZE) undoStack.removeAt(0)
                val next = redoStack.removeAt(redoStack.lastIndex)
                val newState = current.copy(cells = next)
                recomputeNumberCounts(newState)
                _state.value = newState
                updateStackStates()
            }
        }
    }

    fun useHint() {
        viewModelScope.launch {
            stateMutex.withLock {
                val current = _state.value ?: return@withLock
                if (current.isCompleted) return@withLock
                if (_activeHint.value != null) return@withLock

                // Point out a wrong entry first — it derails any further reasoning. Free.
                val wrong = findWrongCell(current)
                if (wrong != null) {
                    _state.value = current.copy(selectedRow = wrong.first, selectedCol = wrong.second)
                    _activeHint.value = Hint(
                        "发现错误",
                        "第${wrong.first + 1}行第${wrong.second + 1}列填错了，照它推理走不下去。先清除这一格再继续。",
                        listOf(wrong),
                        null
                    )
                    _telemetry.value = _telemetry.value.recordHint(null)
                    return@withLock
                }

                if (current.hintsUsed >= GameState.MAX_HINTS) return@withLock

                val board = Array(9) { r -> IntArray(9) { c -> current.cells[r][c].value } }
                val hint = LogicSolver.findHint(board)
                if (hint == null) {
                    _activeHint.value = Hint(
                        "继续观察",
                        "暂时没有可一步推出的格子，试试标记候选数，从候选最少的区域入手。",
                        emptyList(),
                        null
                    )
                    _telemetry.value = _telemetry.value.recordHint(null)
                    return@withLock
                }

                val placement = hint.placement
                _state.value = current.copy(
                    selectedRow = placement?.row ?: current.selectedRow,
                    selectedCol = placement?.col ?: current.selectedCol,
                    hintsUsed = current.hintsUsed + 1
                )
                _activeHint.value = hint
                _telemetry.value = _telemetry.value.recordHint(hint.techniqueName)
            }
        }
    }

    /** Fills in the placement of the currently shown hint, if it has one. */
    fun applyHint() {
        viewModelScope.launch {
            stateMutex.withLock {
                val placement = _activeHint.value?.placement
                _activeHint.value = null
                if (placement == null) return@withLock
                val current = _state.value ?: return@withLock
                if (current.isCompleted) return@withLock
                val cell = current.getCell(placement.row, placement.col)
                if (cell.isGiven) return@withLock

                pushUndo(current.cells)
                _state.value = commitValue(current, placement.row, placement.col, cell.value, placement.value)
                    .copy(selectedRow = placement.row, selectedCol = placement.col)
            }
        }
    }

    fun dismissHint() {
        _activeHint.value = null
    }

    // ========== Demo player (offline step-by-step walkthrough) ==========

    fun startDemo() {
        val current = _state.value ?: return
        if (current.isCompleted || _demo.value != null) return

        // A wrong entry makes the trajectory meaningless — surface it first, like a hint.
        val wrong = findWrongCell(current)
        if (wrong != null) {
            _state.value = current.copy(selectedRow = wrong.first, selectedCol = wrong.second)
            _activeHint.value = Hint(
                "发现错误",
                "第${wrong.first + 1}行第${wrong.second + 1}列填错了，先清除它再看演示。",
                listOf(wrong),
                null
            )
            return
        }

        val board = Array(9) { r -> IntArray(9) { c -> current.cells[r][c].value } }
        val steps = LogicSolver.demoTrajectory(board)
        if (steps.isEmpty()) return

        _activeHint.value = null
        _demoChallenge.value = null
        aiHistory = emptyList()
        _coachReply.value = null
        _demo.value = DemoController(steps)
        _telemetry.value = _telemetry.value.recordDemoViewed()
        timerJob?.cancel()
        timerJob = null
    }

    fun demoNext() = navigateDemo { next() }

    fun demoPrev() = navigateDemo { prev() }

    fun demoReplay() = navigateDemo { replay() }

    /** Moves the demo controller and clears any per-step challenge / coach reply. */
    private fun navigateDemo(move: DemoController.() -> DemoController) {
        _demoChallenge.value = null
        _coachReply.value = null
        _demo.value = _demo.value?.move()
    }

    fun exitDemo() {
        stopAiWork()
        aiHistory = emptyList()
        _coachReply.value = null
        _demo.value = null
        _demoChallenge.value = null
        resumeTimer()
    }

    /** Starts a "your turn" challenge on the current step, if it concludes with a placement. */
    fun startChallenge() {
        val step = _demo.value?.current ?: return
        val p = step.placement ?: return
        _demoChallenge.value = DemoChallenge(p.row, p.col, p.value, step.techniqueName)
    }

    fun submitChallenge(value: Int) {
        val ch = _demoChallenge.value ?: return
        if (ch.result != null) return
        val correct = value == ch.answer
        _demoChallenge.value = ch.copy(
            result = if (correct) DemoChallenge.Result.CORRECT else DemoChallenge.Result.WRONG
        )
        _telemetry.value = _telemetry.value.recordChallenge(correct, ch.technique)
    }

    fun dismissChallenge() {
        _demoChallenge.value = null
    }

    /**
     * Sends a typed/spoken intent to the AI coach. The coach may navigate the demo
     * (driving the same [DemoController]) and/or explain; its reply lands in
     * [coachReply]. Runs in [aiScope] — NOT viewModelScope — so it is cancelled when
     * the screen stops, not only when the VM is cleared.
     */
    fun askCoach(text: String) {
        val intent = text.trim()
        if (intent.isEmpty() || _aiBusy.value) return
        val provider = activeProvider.value ?: return
        val controller = _demo.value ?: return
        val current = _state.value ?: return
        _aiBusy.value = true
        aiScope.launch {
            try {
                val givens = Array(9) { r ->
                    IntArray(9) { c -> current.cells[r][c].let { if (it.isGiven) it.value else 0 } }
                }
                val turn = coach.respond(provider, controller, givens, aiHistory, intent)
                aiHistory = turn.history
                _demoChallenge.value = null
                _demo.value = turn.controller
                _coachReply.value = turn.narration.ifBlank { "（没有更多说明）" }
                _telemetry.value = _telemetry.value.recordAiUsed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _coachReply.value = "⚠️ ${e.message ?: "AI 调用失败"}"
            } finally {
                _aiBusy.value = false
            }
        }
    }

    /**
     * Cancels any in-flight AI request. Bound to the screen's ON_STOP — must NOT rely
     * on viewModelScope, which survives backgrounding (it would keep the request and
     * its network connection alive in the background).
     */
    fun stopAiWork() {
        aiScope.cancel()
        aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        _aiBusy.value = false
    }

    private fun clearDemo() {
        stopAiWork()
        aiHistory = emptyList()
        _coachReply.value = null
        _demo.value = null
        _demoChallenge.value = null
    }

    private fun findWrongCell(state: GameState): Pair<Int, Int>? {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = state.cells[r][c]
                if (!cell.isGiven && cell.value != 0 && cell.value != state.solution[r][c]) {
                    return r to c
                }
            }
        }
        return null
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        val current = _state.value
        val elapsed = _elapsedSeconds.value
        if (current != null && !current.isCompleted) {
            viewModelScope.launch(Dispatchers.IO) {
                saveMutex.withLock {
                    gameRepo.saveGame(current, elapsed)
                    _hasSavedGame.value = true
                }
            }
        }
    }

    fun resumeTimer() {
        val current = _state.value ?: return
        // Stay paused while the demo overlay is up (a learning view, not play time).
        if (!current.isCompleted && timerJob == null && _demo.value == null) startTimer()
    }

    private fun stopAllJobs() {
        timerJob?.cancel()
        timerJob = null
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    private fun onGameCompleted(difficulty: Difficulty, timeSeconds: Long) {
        viewModelScope.launch {
            statsRepo.recordGameCompleted(difficulty, timeSeconds)
            saveMutex.withLock { gameRepo.deleteSave() }
            _hasSavedGame.value = false
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _elapsedSeconds.value++
            }
        }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_SAVE_INTERVAL_MS)
                val snapshot = _state.value ?: continue
                val elapsed = _elapsedSeconds.value
                if (!snapshot.isCompleted) {
                    saveMutex.withLock {
                        gameRepo.saveGame(snapshot, elapsed)
                        _hasSavedGame.value = true
                    }
                }
            }
        }
    }

    private fun recomputeNumberCounts(state: GameState) {
        _numberCounts.value = (1..9).associateWith { num ->
            SudokuValidator.countValue(state.cells, num)
        }
    }

    private fun updateNumberCountsIncremental(oldValue: Int, newValue: Int) {
        if (oldValue == newValue) return
        val counts = _numberCounts.value.toMutableMap()
        if (oldValue in 1..9) counts[oldValue] = (counts[oldValue] ?: 0) - 1
        if (newValue in 1..9) counts[newValue] = (counts[newValue] ?: 0) + 1
        _numberCounts.value = counts
    }

    private fun pushUndo(cells: List<List<Cell>>) {
        undoStack.add(cells)
        if (undoStack.size > MAX_STACK_SIZE) undoStack.removeAt(0)
        redoStack.clear()
        updateStackStates()
    }

    private fun updateStackStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun removeNotesFromPeers(
        cells: MutableList<MutableList<Cell>>, row: Int, col: Int, value: Int
    ) {
        for (i in 0 until 9) {
            if (i != col && value in cells[row][i].notes)
                cells[row][i] = cells[row][i].copy(notes = cells[row][i].notes - value)
            if (i != row && value in cells[i][col].notes)
                cells[i][col] = cells[i][col].copy(notes = cells[i][col].notes - value)
        }
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (r != row && c != col && value in cells[r][c].notes)
                    cells[r][c] = cells[r][c].copy(notes = cells[r][c].notes - value)
            }
        }
    }

    private fun updateErrors(cells: MutableList<MutableList<Cell>>): MutableList<MutableList<Cell>> {
        val conflicts = SudokuValidator.findConflicts(cells.map { it.toList() })
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val isError = Pair(r, c) in conflicts && !cells[r][c].isGiven
                if (cells[r][c].isError != isError)
                    cells[r][c] = cells[r][c].copy(isError = isError)
            }
        }
        return cells
    }

    companion object {
        private const val MAX_STACK_SIZE = 100
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L
    }
}
