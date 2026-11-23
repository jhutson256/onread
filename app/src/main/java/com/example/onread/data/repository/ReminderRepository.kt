package com.example.onread.data.repository

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.util.MessageDeduplicator
import com.example.onread.util.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderRepository(
    private val reminderDao: ReminderDao
) {

    private val insertionMutex = Mutex()

    val allReminders: Flow<List<ReminderEntity>> =
        reminderDao.getAllReminders()

    fun getPendingReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getPendingReminders()
    }

    fun getHistoryReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getHistoryReminders()
    }

    fun getReminderById(id: Long): Flow<ReminderEntity?> {
        return reminderDao.getReminderById(id)
    }

    fun getRemindersByStatus(
        status: String
    ): Flow<List<ReminderEntity>> {
        return reminderDao.getRemindersByStatus(status)
    }

    /**
     * Replaces pending reminders for one incoming conversation and inserts the
     * new reminder while holding the process-wide replacement lock.  A valid
     * thread ID is authoritative; phone matching is used only when the
     * incoming message has no usable thread ID.
     */
    suspend fun replacePendingReminderForIncomingMessage(
        context: Context,
        reminder: ReminderEntity
    ): Long = replacementMutex.withLock {
        val sourceType = reminder.sourceType
        val sourceMessageId = reminder.sourceMessageId
        if (!sourceType.isNullOrBlank() && !sourceMessageId.isNullOrBlank()) {
            val existing = reminderDao.getReminderBySource(sourceType, sourceMessageId)
            if (existing != null) {
                Log.d(
                    "ONREAD_CONSISTENCY",
                    "Skipping duplicate incoming source: type=$sourceType, " +
                        "sourceMessageId=$sourceMessageId, existingReminderId=${existing.id}, " +
                        "status=${existing.status}"
                )
                return@withLock -1L
            }
        }

        val pendingReminders = reminderDao.getPendingRemindersList()
        val matchingReminders = pendingReminders.filter { oldReminder ->
            if (reminder.threadId > 0L) {
                oldReminder.threadId == reminder.threadId
            } else {
                isPhoneNumberMatch(oldReminder.phoneNumber, reminder.phoneNumber)
            }
        }

        Log.d(
            "ONREAD_CONSISTENCY",
            "Incoming sourceType=${reminder.sourceType}, sourceMessageId=${reminder.sourceMessageId}, " +
                "sender=${reminder.phoneNumber}, threadId=${reminder.threadId}; " +
                "matching contact/thread IDs=${matchingReminders.map { it.id }}; " +
                "preserved unrelated pending IDs=${pendingReminders.map { it.id } - matchingReminders.map { it.id }.toSet()}"
        )

        for (oldReminder in matchingReminders) {
            NotificationHelper.cancelReminderAlarm(context, oldReminder.id)
            try {
                NotificationManagerCompat.from(context).cancel(oldReminder.id.toInt())
                Log.d("ONREAD_CONSISTENCY", "Cancelled notification ID ${oldReminder.id}")
            } catch (e: Exception) {
                Log.w("ONREAD_CONSISTENCY", "Failed to cancel notification ID ${oldReminder.id}", e)
            }
            reminderDao.updateReminder(oldReminder.copy(status = "DISMISSED"))
            Log.d("ONREAD_CONSISTENCY", "Dismissed old reminder ID ${oldReminder.id}")
        }

        val newId = reminderDao.insertReminder(reminder)
        if (newId <= 0L) {
            Log.d(
                "ONREAD_CONSISTENCY",
                "Skipped duplicate database insert for sourceType=${reminder.sourceType}, " +
                    "sourceMessageId=${reminder.sourceMessageId}"
            )
            return@withLock -1L
        }
        Log.d(
            "ONREAD_CONSISTENCY",
            "Inserted newest reminder ID $newId for sender=${reminder.phoneNumber}, threadId=${reminder.threadId}"
        )
        NotificationHelper.scheduleReminderAlarm(context, newId, reminder.triggerTime)
        Log.d("ONREAD_CONSISTENCY", "Scheduled alarm for newest reminder ID $newId")
        newId
    }

    suspend fun insertReminder(
        reminder: ReminderEntity,
        skipDeduplication: Boolean = false
    ): Long = insertionMutex.withLock {
        val isDuplicate = if (!skipDeduplication) {
            MessageDeduplicator.isDuplicateMessage(
                reminder.phoneNumber,
                reminder.messageBody,
                reminder.timestamp
            )
        } else {
            false
        }

        val phoneSuffix = reminder.phoneNumber.takeLast(10)
        val cutoffTime = reminder.timestamp - 30_000L

        val isDbDuplicate = reminderDao.hasRecentPendingReminder(
            phoneNumber = reminder.phoneNumber,
            phoneSuffix = phoneSuffix,
            messageBody = reminder.messageBody,
            cutoffTime = cutoffTime
        )

        if (isDuplicate || isDbDuplicate) {
            try {
                Log.d(
                    "ONREAD_DEDUP",
                    "DROPPED DUPLICATE: ${reminder.phoneNumber}"
                )
            } catch (_: Throwable) {
            }

            return@withLock -1L
        }

        val id = reminderDao.insertReminder(reminder)

        try {
            Log.d(
                "ONREAD_CONSISTENCY",
                "insertReminder succeeded: " +
                    "id=$id, " +
                    "phone=${reminder.phoneNumber}, " +
                    "name=${reminder.contactName}, " +
                    "threadId=${reminder.threadId}"
            )
        } catch (_: Throwable) {
        }

        return@withLock id
    }

    suspend fun isDuplicateReminder(
        phoneNumber: String,
        messageBody: String,
        timestamp: Long
    ): Boolean {
        if (
            MessageDeduplicator.isDuplicateMessage(
                phoneNumber,
                messageBody,
                timestamp
            )
        ) {
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
        phoneNumber: String
    ) {
        dismissPendingRemindersForNumber(
            context = null,
            phoneNumber = phoneNumber,
            exceptReminderId = null
        )
    }

    suspend fun dismissPendingRemindersForNumber(
        context: Context?,
        phoneNumber: String,
        exceptReminderId: Long? = null
    ) {
        if (phoneNumber.isBlank()) {
            return
        }

        val pendingReminders =
            reminderDao.getPendingRemindersList()

        for (reminder in pendingReminders) {
            if (reminder.id == exceptReminderId) {
                continue
            }

            if (
                isPhoneNumberMatch(
                    reminder.phoneNumber,
                    phoneNumber
                )
            ) {
                reminderDao.updateReminder(
                    reminder.copy(status = "DISMISSED")
                )
                Log.d(
                    "ONREAD_CONSISTENCY",
                    "Reminder ID ${reminder.id} status changed ${reminder.status} -> DISMISSED; " +
                        "reason=outgoing message for normalized phone=$phoneNumber"
                )

                if (context != null) {
                    NotificationHelper.cancelReminderAlarm(
                        context,
                        reminder.id
                    )

                    try {
                        NotificationManagerCompat
                            .from(context)
                            .cancel(reminder.id.toInt())
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    suspend fun updateReminder(
        reminder: ReminderEntity
    ) {
        val previous = reminderDao.getReminderById(reminder.id).first()
        reminderDao.updateReminder(reminder)
        if (previous?.status != reminder.status) {
            Log.d(
                "ONREAD_CONSISTENCY",
                "Reminder ID ${reminder.id} status changed ${previous?.status} -> ${reminder.status}; " +
                    "reason=repository update"
            )
        }
    }

    suspend fun deleteReminder(
        reminder: ReminderEntity
    ) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun deleteReminderById(
        id: Long
    ) {
        reminderDao.deleteReminderById(id)
    }

    companion object {
        private val replacementMutex = Mutex()

        fun isPhoneNumberMatch(
            num1: String,
            num2: String
        ): Boolean {
            if (num1 == num2) {
                return true
            }

            val digits1 = num1.filter { it.isDigit() }
            val digits2 = num2.filter { it.isDigit() }

            if (digits1.isEmpty() || digits2.isEmpty()) {
                return false
            }

            if (digits1 == digits2) {
                return true
            }

            // Accept a country-code variant only when both values are full
            // phone numbers. Never match arbitrary partial suffixes.
            return digits1.length >= 10 &&
                digits2.length >= 10 &&
                digits1.takeLast(10) == digits2.takeLast(10)
        }
    }
}
