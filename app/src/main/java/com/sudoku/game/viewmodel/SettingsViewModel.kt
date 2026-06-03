package com.sudoku.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sudoku.game.ai.AiClient
import com.sudoku.game.data.ProviderRepository
import com.sudoku.game.model.AiProvider
import com.sudoku.game.model.AiSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProviderRepository(application)
    private val client = AiClient()

    val settings: StateFlow<AiSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettings())

    fun saveProvider(provider: AiProvider) = viewModelScope.launch { repo.upsertProvider(provider) }
    fun deleteProvider(id: String) = viewModelScope.launch { repo.removeProvider(id) }
    fun selectProvider(id: String) = viewModelScope.launch { repo.setActive(id) }

    /** Connection test + `/models` fetch in one — success means reachable & key valid,
     *  and the returned ids populate the editor's model suggestions (third source). */
    suspend fun fetchModels(provider: AiProvider): Result<List<String>> =
        runCatching { client.listModels(provider) }
}
