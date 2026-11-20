package com.example.onread.data.repository

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.util.MessageDeduplicator
import com.example.onread.util.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderRepository(private val reminderDao: ReminderDao) {

    private val insertionMutex = Mutex()

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

    suspend fun insertReminder(reminder: ReminderEntity, skipDeduplication: Boolean = false): Long = insertionMutex.withLock {
        val isDuplicate = if (!skipDeduplication) {
            MessageDeduplicator.isDuplicateMessage(reminder.phoneNumber, reminder.messageBody, reminder.timestamp)
        } else {
            false
        }

        val phoneSuffix = reminder.phoneNumber.takeLast(10)
        val cutoffTime = reminder.timestamp - 30_000L
        val isDbDuplicate = reminderDao.hasRecentPendingReminder(
            phoneNumber = reminder.phoneNumber,
            phoneSuffix = phoneSuffix,
            messageBody = reminder.messageBody,
            cutoffTime = cutoffTime,
        )

        if (isDuplicate || isDbDuplicate) {
            try {
                Log.d("ONREAD_DEDUP", "DROPPED DUPLICATE: ${reminder.phoneNumber}")
            } catch (_: Throwable) {
            }
            return@withLock -1L
        }

        val id = reminderDao.insertReminder(reminder)
        try {
            Log.d("ONREAD_CONSISTENCY", "insertReminder succeeded: id=$id, phone=${reminder.phoneNumber}, name=${reminder.contactName}, threadId=${reminder.threadId}")
        } catch (_: Throwable) {
        }
        return@withLock id
    }

    suspend fun isDuplicateReminder(phoneNumber: String, messageBody: String, timestamp: Long): Boolean {
        if (MessageDeduplicator.isDuplicateMessage(phoneNumber, messageBody, timestamp)) {
            return true
        }
        val cutoffTime = timestamp - 30_000L
        val phoneSuffix = phoneNumber.takeLast(10)
        return reminderDao.hasRecentPendingReminder(
            phoneNumber = phoneNumber,
            phoneSuffix = phoneSuffix,
            messageBody = messageBody,
            cutoffTime = cutoffTime
        )
    }

    suspend fun dismissPendingRemindersForNumber(phoneNumber: String) {
        dismissPendingRemindersForNumber(context = null, phoneNumber = phoneNumber)
    }

    suspend fun dismissPendingRemindersForNumber(context: Context?, phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        val pendingReminders = reminderDao.getPendingRemindersList()
        val inputDigits = phoneNumber.filter { it.isDigit() }
        val inputSuffix = if (inputDigits.length >= 10) inputDigits.takeLast(10) else inputDigits

        for (reminder in pendingReminders) {
            if (isPhoneNumberMatch(reminder.phoneNumber, phoneNumber)) {
                val updated = reminder.copy(status = "DISMISSED")
                reminderDao.updateReminder(updated)
                if (context != null) {
                    NotificationHelper.cancelReminderAlarm(context, reminder.id)
                    try {
                        NotificationManagerCompat.from(context).cancel(reminder.id.toInt())
                    } catch (_: Exception) {
                    }
                }
            }
        }
        val normalizedAddress = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (normalizedAddress.isNotEmpty()) {
            reminderDao.dismissRemindersForNumber(normalizedAddress)
        }
        reminderDao.dismissRemindersForNumber(phoneNumber)
        if (inputSuffix.length >= 7) {
            reminderDao.dismissRemindersForPhoneSuffix(inputSuffix)
        }
    }

    companion object {
        fun isPhoneNumberMatch(num1: String, num2: String): Boolean {
            if (num1 == num2) return true
            val digits1 = num1.filter { it.isDigit() }
            val digits2 = num2.filter { it.isDigit() }
            if (digits1.isEmpty() || digits2.isEmpty()) return false
            if (digits1 == digits2) return true
            val suffix1 = if (digits1.length >= 10) digits1.takeLast(10) else digits1
            val suffix2 = if (digits2.length >= 10) digits2.takeLast(10) else digits2
            if (suffix1.length >= 7 && suffix2.length >= 7 && suffix1 == suffix2) return true
            if (digits1.length >= 7 && digits2.length >= 7 && (digits1.endsWith(digits2) || digits2.endsWith(digits1))) return true
            return false
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
