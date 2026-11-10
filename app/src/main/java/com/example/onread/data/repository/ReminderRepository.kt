package com.example.onread.data.repository

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.util.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class ReminderRepository(private val reminderDao: ReminderDao) {

    private val messageDeduplicationCache = ConcurrentHashMap<String, Long>()
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

    suspend fun insertReminder(reminder: ReminderEntity): Long = insertionMutex.withLock {
        val now = System.currentTimeMillis()
        val refTime = maxOf(now, reminder.timestamp)

        // Purge cache entries older than 60,000 ms (or 30,000 ms window relative to message timestamp)
        messageDeduplicationCache.entries.removeIf { 
            ((refTime - it.value) > 30_000L) || ((now - it.value) > 60_000L)
        }

        val phoneSuffix = reminder.phoneNumber.takeLast(10)
        val key = "$phoneSuffix:${reminder.messageBody.trim().lowercase()}"

        val cutoffTime = reminder.timestamp - 30_000L
        val isDbDuplicate = reminderDao.hasRecentPendingReminder(
            phoneNumber = reminder.phoneNumber,
            phoneSuffix = phoneSuffix,
            messageBody = reminder.messageBody,
            cutoffTime = cutoffTime,
        )

        if (messageDeduplicationCache.containsKey(key) || isDbDuplicate) {
            try {
                Log.d("ONREAD_DEDUP", "DROPPED DUPLICATE: $key")
            } catch (_: Throwable) {
            }
            return@withLock -1L
        }

        messageDeduplicationCache[key] = refTime
        val id = reminderDao.insertReminder(reminder)
        try {
            Log.d("ONREAD_CONSISTENCY", "insertReminder succeeded: id=$id, phone=${reminder.phoneNumber}, name=${reminder.contactName}, threadId=${reminder.threadId}")
        } catch (_: Throwable) {
        }
        return@withLock id
    }

    suspend fun isDuplicateReminder(phoneNumber: String, messageBody: String, timestamp: Long): Boolean {
        val cutoffTime = timestamp - 30_000L // 30 seconds window
        val phoneSuffix = phoneNumber.takeLast(10)
        val key = "$phoneSuffix:${messageBody.trim().lowercase()}"

        if (messageDeduplicationCache.containsKey(key)) {
            val cachedTime = messageDeduplicationCache[key]
            if (cachedTime != null && abs(timestamp - cachedTime) <= 30_000L) {
                return true
            }
        }

        return reminderDao.hasRecentPendingReminder(
            phoneNumber = phoneNumber,
            phoneSuffix = phoneSuffix,
            messageBody = messageBody,
            cutoffTime = cutoffTime
        )
    }

    suspend fun dismissPendingRemindersForNumber(context: Context, phoneNumber: String) {
        val pendingReminders = reminderDao.getPendingRemindersList()
        val inputDigits = phoneNumber.replace(Regex("[^0-9]"), "")

        for (reminder in pendingReminders) {
            val existingDigits = reminder.phoneNumber.replace(Regex("[^0-9]"), "")
            val phoneMatches = (reminder.phoneNumber == phoneNumber) ||
                (inputDigits.isNotEmpty() && existingDigits.isNotEmpty() && 
                    ((existingDigits == inputDigits) ||
                     (inputDigits.length >= 10 && existingDigits.length >= 10 && inputDigits.takeLast(10) == existingDigits.takeLast(10))))

            if (phoneMatches) {
                val updated = reminder.copy(status = "DISMISSED")
                reminderDao.updateReminder(updated)
                NotificationHelper.cancelReminderAlarm(context, reminder.id)
                try {
                    NotificationManagerCompat.from(context).cancel(reminder.id.toInt())
                } catch (_: Exception) {
                }
            }
        }
        val normalizedAddress = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (normalizedAddress.isNotEmpty()) {
            reminderDao.dismissRemindersForNumber(normalizedAddress)
        }
        reminderDao.dismissRemindersForNumber(phoneNumber)
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
