package com.example.onread.data.repository

import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val reminderDao: ReminderDao) {

    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()

    fun getPendingReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getPendingReminders()
    }

    fun getHistoryReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getHistoryReminders()
    }

    fun getReminderById(id: Long): Flow<ReminderEntity?> {
        return reminderDao.getReminderById(id)
    }

    fun getRemindersByStatus(status: String): Flow<List<ReminderEntity>> {
        return reminderDao.getRemindersByStatus(status)
    }

    suspend fun insertReminder(reminder: ReminderEntity): Long {
        if (isDuplicateReminder(reminder.phoneNumber, reminder.messageBody, reminder.timestamp)) {
            return -1L
        }
        return reminderDao.insertReminder(reminder)
    }

    suspend fun isDuplicateReminder(phoneNumber: String, messageBody: String, timestamp: Long): Boolean {
        val cutoffTime = timestamp - 60_000L // 60 seconds window
        val recentReminders = reminderDao.getRemindersSince(cutoffTime)
        val inputDigits = phoneNumber.replace(Regex("[^0-9]"), "")

        return recentReminders.any { existing ->
            val existingDigits = existing.phoneNumber.replace(Regex("[^0-9]"), "")
            val phoneMatches = (existing.phoneNumber == phoneNumber) ||
                (inputDigits.isNotEmpty() && existingDigits.isNotEmpty() && 
                    ((existingDigits == inputDigits) ||
                     (inputDigits.length >= 10 && existingDigits.length >= 10 && inputDigits.takeLast(10) == existingDigits.takeLast(10))))
            
            val bodyMatches = existing.messageBody.trim().equals(messageBody.trim(), ignoreCase = true)
            val timeWithinWindow = Math.abs(existing.timestamp - timestamp) <= 60_000L

            phoneMatches && bodyMatches && timeWithinWindow
        }
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun deleteReminderById(id: Long) {
        reminderDao.deleteReminderById(id)
    }
}
