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
import com.sudoku.game.model.GameStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepo = GameRepository(application)
    private val statsRepo = StatsRepository(application)
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _hasSavedGame = MutableStateFlow(false)
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

    // Pre-computed number counts to avoid recalculation on every recomposition
    private val _numberCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val numberCounts: StateFlow<Map<Int, Int>> = _numberCounts.asStateFlow()

    val stats: StateFlow<GameStats> = statsRepo.getStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameStats())

    private var timerJob: Job? = null
    private var autoSaveJob: Job? = null
    private val undoStack = mutableListOf<List<List<Cell>>>()
    private val redoStack = mutableListOf<List<List<Cell>>>()

    init {
        viewModelScope.launch {
            _hasSavedGame.value = gameRepo.hasSavedGame()
        }
    }

    fun newGame(difficulty: Difficulty) {
        stopAllJobs()
        undoStack.clear()
        redoStack.clear()

        viewModelScope.launch(Dispatchers.Default) {
            _isGenerating.value = true
            val gameState = SudokuGenerator.generate(difficulty)
            _state.value = gameState
            _isGenerating.value = false
            updateNumberCounts(gameState)
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
                undoStack.clear()
                redoStack.clear()
                updateNumberCounts(saved)
                startTimer()
                startAutoSave()
            }
        }
    }

    fun exitGame() {
        saveNow()
        stopAllJobs()
        _state.value = null
    }

    fun selectCell(row: Int, col: Int) {
        if (row !in 0..8 || col !in 0..8) return
        _state.update { it?.copy(selectedCell = Pair(row, col)) }
    }

    fun inputNumber(number: Int) {
        if (number !in 1..9) return
        _state.update { current ->
            if (current == null || current.isCompleted) return@update current
            val (row, col) = current.selectedCell ?: return@update current
            val cell = current.getCell(row, col)
            if (cell.isGiven) return@update current

            saveUndoState(current.cells)
            redoStack.clear()

            if (current.isNoteMode) {
                applyNote(current, row, col, number)
            } else {
                applyValue(current, row, col, number)
            }
        }
    }

    private fun applyValue(current: GameState, row: Int, col: Int, number: Int): GameState {
        val newCells = current.cells.map { it.toMutableList() }.toMutableList()
        val cell = newCells[row][col]

        val newValue = if (cell.value == number) 0 else number
        newCells[row][col] = cell.copy(value = newValue, notes = emptySet())

        if (newValue != 0) {
            removeNotesFromPeers(newCells, row, col, newValue)
        }

        val updatedCells = updateErrors(newCells)
        val cellsList = updatedCells.map { it.toList() }
        val isCompleted = SudokuValidator.isComplete(cellsList)

        if (isCompleted) {
            timerJob?.cancel()
            onGameCompleted(current.difficulty, current.elapsedSeconds)
        }

        val newState = current.copy(cells = cellsList, isCompleted = isCompleted)
        updateNumberCounts(newState)
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
        _state.update { current ->
            if (current == null || current.isCompleted) return@update current
            val (row, col) = current.selectedCell ?: return@update current
            val cell = current.getCell(row, col)
            if (cell.isGiven) return@update current

            saveUndoState(current.cells)
            redoStack.clear()

            val newCells = current.cells.map { it.toMutableList() }.toMutableList()
            newCells[row][col] = cell.copy(value = 0, notes = emptySet())
            val updatedCells = updateErrors(newCells)
            val newState = current.copy(cells = updatedCells.map { it.toList() })
            updateNumberCounts(newState)
            newState
        }
    }

    fun toggleNoteMode() {
        _state.update { it?.copy(isNoteMode = !it.isNoteMode) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        _state.update { current ->
            if (current == null) return@update null
            redoStack.add(current.cells)
            if (redoStack.size > MAX_STACK_SIZE) redoStack.removeAt(0)
            val previous = undoStack.removeAt(undoStack.lastIndex)
            val newState = current.copy(cells = previous)
            updateNumberCounts(newState)
            newState
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        _state.update { current ->
            if (current == null) return@update null
            undoStack.add(current.cells)
            val next = redoStack.removeAt(redoStack.lastIndex)
            val newState = current.copy(cells = next)
            updateNumberCounts(newState)
            newState
        }
    }

    fun useHint() {
        _state.update { current ->
            if (current == null || current.isCompleted) return@update current
            if (current.hintsUsed >= GameState.MAX_HINTS) return@update current

            val cells = current.cells
            for (r in 0 until 9) {
                for (c in 0 until 9) {
                    val cell = cells[r][c]
                    if (!cell.isGiven && (cell.value == 0 || cell.value != current.solution[r][c])) {
                        saveUndoState(current.cells)
                        redoStack.clear()

                        val newCells = cells.map { it.toMutableList() }.toMutableList()
                        newCells[r][c] = cell.copy(
                            value = current.solution[r][c],
                            notes = emptySet(),
                            isError = false
                        )
                        removeNotesFromPeers(newCells, r, c, current.solution[r][c])
                        val updatedCells = updateErrors(newCells)
                        val cellsList = updatedCells.map { it.toList() }
                        val isCompleted = SudokuValidator.isComplete(cellsList)
                        if (isCompleted) {
                            timerJob?.cancel()
                            onGameCompleted(current.difficulty, current.elapsedSeconds)
                        }
                        val newState = current.copy(
                            cells = cellsList,
                            selectedCell = Pair(r, c),
                            hintsUsed = current.hintsUsed + 1,
                            isCompleted = isCompleted
                        )
                        updateNumberCounts(newState)
                        return@update newState
                    }
                }
            }
            current
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        saveNow()
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

    private fun saveNow() {
        val current = _state.value ?: return
        if (current.isCompleted) return
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                gameRepo.saveGame(current)
                _hasSavedGame.value = true
            }
        }
    }

    private fun onGameCompleted(difficulty: Difficulty, timeSeconds: Long) {
        viewModelScope.launch {
            statsRepo.recordGameCompleted(difficulty, timeSeconds)
            saveMutex.withLock {
                gameRepo.deleteSave()
            }
            _hasSavedGame.value = false
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it?.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_SAVE_INTERVAL_MS)
                val current = _state.value ?: continue
                if (!current.isCompleted) {
                    saveMutex.withLock {
                        gameRepo.saveGame(current)
                        _hasSavedGame.value = true
                    }
                }
            }
        }
    }

    private fun updateNumberCounts(state: GameState) {
        _numberCounts.value = (1..9).associateWith { num ->
            SudokuValidator.countValue(state.cells, num)
        }
    }

    private fun saveUndoState(cells: List<List<Cell>>) {
        undoStack.add(cells)
        if (undoStack.size > MAX_STACK_SIZE) undoStack.removeAt(0)
    }

    private fun removeNotesFromPeers(
        cells: MutableList<MutableList<Cell>>,
        row: Int, col: Int, value: Int
    ) {
        for (i in 0 until 9) {
            if (i != col && value in cells[row][i].notes) {
                cells[row][i] = cells[row][i].copy(notes = cells[row][i].notes - value)
            }
            if (i != row && value in cells[i][col].notes) {
                cells[i][col] = cells[i][col].copy(notes = cells[i][col].notes - value)
            }
        }
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (r != row && c != col && value in cells[r][c].notes) {
                    cells[r][c] = cells[r][c].copy(notes = cells[r][c].notes - value)
                }
            }
        }
    }

    private fun updateErrors(cells: MutableList<MutableList<Cell>>): MutableList<MutableList<Cell>> {
        val conflicts = SudokuValidator.findConflicts(cells.map { it.toList() })
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val isError = Pair(r, c) in conflicts && !cells[r][c].isGiven
                if (cells[r][c].isError != isError) {
                    cells[r][c] = cells[r][c].copy(isError = isError)
                }
            }
        }
        return cells
    }

    companion object {
        private const val MAX_STACK_SIZE = 100
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L
    }
}
