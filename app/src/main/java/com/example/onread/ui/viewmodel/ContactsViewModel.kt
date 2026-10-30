package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.repository.ContactRepository
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.util.ContactSyncHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val contactRepository = ContactRepository(database.contactDao())
    private val settingsRepository = SettingsRepository(application)

    val contactSettings: StateFlow<List<ContactSettingEntity>> = contactRepository.allContactSettings
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultDelayMinutes: StateFlow<Int> = settingsRepository.defaultDelayMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val useSpecificTime: StateFlow<Boolean> = settingsRepository.useSpecificTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val globalSpecificHour: StateFlow<Int> = settingsRepository.specificTimeHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18)

    val globalSpecificMinute: StateFlow<Int> = settingsRepository.specificTimeMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun syncContacts() {
        if (_isSyncing.value) return

        _isSyncing.value = true

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ContactSyncHelper.syncContacts(
                        getApplication(),
                        database.contactDao()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updateContactSetting(setting: ContactSettingEntity) {
        viewModelScope.launch {
            contactRepository.insertOrUpdateContactSetting(setting)
        }
    }
}
