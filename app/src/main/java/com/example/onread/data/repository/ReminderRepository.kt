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

    suspend fun dismissPendingRemindersForNumber(
        context: Context,
        phoneNumber: String,
        exceptReminderId: Long? = null
    ) {
        val inputDigits = phoneNumber.replace(Regex("[^0-9]"), "")
        val phoneSuffix = inputDigits.takeLast(10)

        val pendingReminders = reminderDao.getPendingRemindersForNumber(
            phoneNumber = phoneNumber,
            phoneSuffix = phoneSuffix
        )

        for (reminder in pendingReminders) {
            if (reminder.id == exceptReminderId) {
                continue
            }

            val existingDigits = reminder.phoneNumber.replace(Regex("[^0-9]"), "")

            val phoneMatches =
                reminder.phoneNumber == phoneNumber ||
                        (
                                inputDigits.length >= 10 &&
                                        existingDigits.length >= 10 &&
                                        inputDigits.takeLast(10) == existingDigits.takeLast(10)
                                )

            if (phoneMatches) {
                reminderDao.updateReminder(
                    reminder.copy(status = "DISMISSED")
                )

                // Cancel the alarm that would have shown the old reminder.
                NotificationHelper.cancelReminderAlarm(context, reminder.id)

                // Also cancel it in case the notification was already displayed.
                try {
                    NotificationManagerCompat
                        .from(context)
                        .cancel(reminder.id.toInt())
                } catch (_: Exception) {
                }
            }
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
