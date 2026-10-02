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

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val contactRepository = ContactRepository(database.contactDao())
    private val settingsRepository = SettingsRepository(application)

    val contactSettings: StateFlow<List<ContactSettingEntity>> = contactRepository.allContactSettings
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultDelayMinutes: StateFlow<Int> = settingsRepository.defaultDelayMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    fun syncContacts() {
        viewModelScope.launch {
            try {
                ContactSyncHelper.syncContacts(getApplication(), database.contactDao())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateContactSetting(setting: ContactSettingEntity) {
        viewModelScope.launch {
            contactRepository.insertOrUpdateContactSetting(setting)
        }
    }
}
