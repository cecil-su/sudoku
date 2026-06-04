package com.sudoku.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sudoku.game.data.GameSettingsRepository
import com.sudoku.game.model.ErrorCheckMode
import com.sudoku.game.model.GameSettings
import com.sudoku.game.model.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Exposes persisted [GameSettings] and its setters, mirroring [SettingsViewModel]. */
class GameSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = GameSettingsRepository(application)

    val settings: StateFlow<GameSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GameSettings())

    fun setTheme(theme: ThemeChoice) = viewModelScope.launch { repo.setTheme(theme) }
    fun setErrorCheck(mode: ErrorCheckMode) = viewModelScope.launch { repo.setErrorCheck(mode) }
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { repo.setSoundEnabled(enabled) }
    fun setAutoRemoveNotes(enabled: Boolean) = viewModelScope.launch { repo.setAutoRemoveNotes(enabled) }
    fun setShowTimer(enabled: Boolean) = viewModelScope.launch { repo.setShowTimer(enabled) }
}
