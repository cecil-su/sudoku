package com.sudoku.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sudoku.game.data.GameRepository
import com.sudoku.game.data.StatsRepository
import com.sudoku.game.engine.SudokuGenerator
import com.sudoku.game.engine.SudokuValidator
import com.sudoku.game.model.Cell
import com.sudoku.game.model.Difficulty
import com.sudoku.game.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepo = GameRepository(application)
    private val statsRepo = StatsRepository(application)
    private val saveMutex = Mutex()

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
        _state.value = _state.value?.copy(selectedRow = row, selectedCol = col)
    }

    fun inputNumber(number: Int) {
        if (number !in 1..9) return
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
        val newCells = current.cells.map { it.toMutableList() }.toMutableList()
        val cell = newCells[row][col]
        val oldValue = cell.value
        val newValue = if (oldValue == number) 0 else number
        newCells[row][col] = cell.copy(value = newValue, notes = emptySet())

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

        val newState = current.copy(cells = cellsList, isCompleted = isCompleted)
        updateNumberCountsIncremental(oldValue, newValue)
        return newState
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
                if (current.hintsUsed >= GameState.MAX_HINTS) return@withLock

                // Prioritize selected cell
                val targetRow: Int
                val targetCol: Int
                if (current.hasSelection) {
                    val sel = current.getCell(current.selectedRow, current.selectedCol)
                    if (!sel.isGiven && (sel.value == 0 || sel.value != current.solution[current.selectedRow][current.selectedCol])) {
                        targetRow = current.selectedRow
                        targetCol = current.selectedCol
                    } else {
                        val found = findHintTarget(current) ?: return@withLock
                        targetRow = found.first
                        targetCol = found.second
                    }
                } else {
                    val found = findHintTarget(current) ?: return@withLock
                    targetRow = found.first
                    targetCol = found.second
                }

                pushUndo(current.cells)

                val cell = current.getCell(targetRow, targetCol)
                val oldValue = cell.value
                val correctValue = current.solution[targetRow][targetCol]
                val newCells = current.cells.map { it.toMutableList() }.toMutableList()
                newCells[targetRow][targetCol] = cell.copy(
                    value = correctValue, notes = emptySet(), isError = false
                )
                removeNotesFromPeers(newCells, targetRow, targetCol, correctValue)
                val updatedCells = updateErrors(newCells)
                val cellsList = updatedCells.map { it.toList() }
                val isCompleted = SudokuValidator.isComplete(cellsList)
                if (isCompleted) {
                    timerJob?.cancel()
                    onGameCompleted(current.difficulty, _elapsedSeconds.value)
                }
                _state.value = current.copy(
                    cells = cellsList,
                    selectedRow = targetRow,
                    selectedCol = targetCol,
                    hintsUsed = current.hintsUsed + 1,
                    isCompleted = isCompleted
                )
                updateNumberCountsIncremental(oldValue, correctValue)
            }
        }
    }

    private fun findHintTarget(state: GameState): Pair<Int, Int>? {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = state.cells[r][c]
                if (!cell.isGiven && (cell.value == 0 || cell.value != state.solution[r][c])) {
                    return Pair(r, c)
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
        if (!current.isCompleted && timerJob == null) startTimer()
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
