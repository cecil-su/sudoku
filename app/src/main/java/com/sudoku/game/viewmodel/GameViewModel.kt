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

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepo = GameRepository(application)
    private val statsRepo = StatsRepository(application)

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _hasSavedGame = MutableStateFlow(false)
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

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
        timerJob?.cancel()
        undoStack.clear()
        redoStack.clear()

        viewModelScope.launch(Dispatchers.Default) {
            _isGenerating.value = true
            val gameState = SudokuGenerator.generate(difficulty)
            _state.value = gameState
            _isGenerating.value = false
            statsRepo.recordGameStarted()
            startTimer()
            startAutoSave()
        }
    }

    fun continueGame() {
        viewModelScope.launch {
            val saved = gameRepo.loadGame()
            if (saved != null && !saved.isCompleted) {
                _state.value = saved
                undoStack.clear()
                redoStack.clear()
                startTimer()
                startAutoSave()
            }
        }
    }

    fun selectCell(row: Int, col: Int) {
        _state.update { current ->
            current?.copy(selectedCell = Pair(row, col))
        }
    }

    fun inputNumber(number: Int) {
        val current = _state.value ?: return
        val (row, col) = current.selectedCell ?: return
        val cell = current.getCell(row, col)
        if (cell.isGiven) return

        saveUndoState(current.cells)
        redoStack.clear()

        if (current.isNoteMode) {
            inputNote(row, col, number)
        } else {
            inputValue(row, col, number)
        }
    }

    private fun inputValue(row: Int, col: Int, number: Int) {
        _state.update { current ->
            if (current == null) return@update null

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

            current.copy(cells = cellsList, isCompleted = isCompleted)
        }
    }

    private fun inputNote(row: Int, col: Int, number: Int) {
        _state.update { current ->
            if (current == null) return@update null
            val cell = current.getCell(row, col)
            if (cell.value != 0) return@update current

            val newNotes = if (number in cell.notes) cell.notes - number else cell.notes + number
            val newCells = current.cells.map { it.toMutableList() }.toMutableList()
            newCells[row][col] = cell.copy(notes = newNotes)

            current.copy(cells = newCells.map { it.toList() })
        }
    }

    fun clearCell() {
        val current = _state.value ?: return
        val (row, col) = current.selectedCell ?: return
        val cell = current.getCell(row, col)
        if (cell.isGiven) return

        saveUndoState(current.cells)
        redoStack.clear()

        _state.update { state ->
            if (state == null) return@update null
            val newCells = state.cells.map { it.toMutableList() }.toMutableList()
            newCells[row][col] = cell.copy(value = 0, notes = emptySet())
            val updatedCells = updateErrors(newCells)
            state.copy(cells = updatedCells.map { it.toList() })
        }
    }

    fun toggleNoteMode() {
        _state.update { it?.copy(isNoteMode = !it.isNoteMode) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _state.value ?: return
        redoStack.add(current.cells)
        val previous = undoStack.removeAt(undoStack.lastIndex)
        _state.update { it?.copy(cells = previous) }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _state.value ?: return
        undoStack.add(current.cells)
        val next = redoStack.removeAt(redoStack.lastIndex)
        _state.update { it?.copy(cells = next) }
    }

    fun useHint() {
        val current = _state.value ?: return
        if (current.hintsUsed >= GameState.MAX_HINTS) return

        val cells = current.cells
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = cells[r][c]
                if (!cell.isGiven && (cell.value == 0 || cell.value != current.solution[r][c])) {
                    saveUndoState(current.cells)
                    redoStack.clear()

                    _state.update { state ->
                        if (state == null) return@update null
                        val newCells = state.cells.map { it.toMutableList() }.toMutableList()
                        newCells[r][c] = cell.copy(
                            value = state.solution[r][c],
                            notes = emptySet(),
                            isError = false
                        )
                        removeNotesFromPeers(newCells, r, c, state.solution[r][c])
                        val updatedCells = updateErrors(newCells)
                        val cellsList = updatedCells.map { it.toList() }
                        val isCompleted = SudokuValidator.isComplete(cellsList)
                        if (isCompleted) {
                            timerJob?.cancel()
                            onGameCompleted(state.difficulty, state.elapsedSeconds)
                        }
                        state.copy(
                            cells = cellsList,
                            selectedCell = Pair(r, c),
                            hintsUsed = state.hintsUsed + 1,
                            isCompleted = isCompleted
                        )
                    }
                    return
                }
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        saveNow()
    }

    fun resumeTimer() {
        val current = _state.value ?: return
        if (!current.isCompleted) startTimer()
    }

    private fun saveNow() {
        val current = _state.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            gameRepo.saveGame(current)
            _hasSavedGame.value = true
        }
    }

    private fun onGameCompleted(difficulty: Difficulty, timeSeconds: Long) {
        viewModelScope.launch {
            statsRepo.recordGameCompleted(difficulty, timeSeconds)
            gameRepo.deleteSave()
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
                delay(30_000) // Auto-save every 30 seconds
                val current = _state.value ?: continue
                if (!current.isCompleted) {
                    gameRepo.saveGame(current)
                    _hasSavedGame.value = true
                }
            }
        }
    }

    private fun saveUndoState(cells: List<List<Cell>>) {
        undoStack.add(cells)
        if (undoStack.size > 100) undoStack.removeAt(0)
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
}
