package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.data.repository.ContactRepository
import com.example.onread.data.repository.ReminderRepository
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.util.ContactSyncHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val contactRepository = ContactRepository(database.contactDao())
    private val reminderRepository = ReminderRepository(database.reminderDao())
    private val settingsRepository = SettingsRepository(application)

    val contactSettings: StateFlow<List<ContactSettingEntity>> = contactRepository.allContactSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = reminderRepository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val colorTheme: StateFlow<String> = settingsRepository.colorThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val defaultDelayMinutes: StateFlow<Int> = settingsRepository.defaultDelayMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val savedContactsOnly: StateFlow<Boolean> = settingsRepository.savedContactsOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun dismissReminder(reminderId: Long) {
        viewModelScope.launch {
            val reminder = reminderRepository.getReminderById(reminderId).first()
            if (reminder != null) {
                reminderRepository.updateReminder(reminder.copy(status = "DISMISSED"))
            }
        }
    }

    fun markReminderHandled(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.updateReminder(reminder.copy(status = "HANDLED"))
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminder)
        }
    }
}
