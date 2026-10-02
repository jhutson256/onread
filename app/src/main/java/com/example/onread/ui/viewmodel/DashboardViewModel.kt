package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val reminderRepository = ReminderRepository(database.reminderDao())

    val reminders: StateFlow<List<ReminderEntity>> = reminderRepository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
