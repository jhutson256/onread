package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    val colorTheme: StateFlow<String> = settingsRepository.colorThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val defaultDelayMinutes: StateFlow<Int> = settingsRepository.defaultDelayMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val savedContactsOnly: StateFlow<Boolean> = settingsRepository.savedContactsOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun updateColorTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.updateColorTheme(theme)
        }
    }

    fun updateDefaultDelayMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateDefaultDelayMinutes(minutes)
        }
    }

    fun updateSavedContactsOnly(savedOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSavedContactsOnly(savedOnly)
        }
    }
}
