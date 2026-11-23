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

    suspend fun replacePendingReminderForIncomingMessage(
        context: Context,
        phoneNumber: String,
        threadId: Long
    ) {
        val pendingReminders =
            reminderDao.getPendingRemindersList()

        for (oldReminder in pendingReminders) {
            val sameThread =
                threadId > 0L &&
                        oldReminder.threadId == threadId

            val samePhone =
                isPhoneNumberMatch(
                    oldReminder.phoneNumber,
                    phoneNumber
                )

            if (sameThread || samePhone) {
                // Cancel the scheduled alarm.
                NotificationHelper.cancelReminderAlarm(
                    context,
                    oldReminder.id
                )

                // Remove any notification that may already be displayed.
                try {
                    NotificationManagerCompat
                        .from(context)
                        .cancel(oldReminder.id.toInt())
                } catch (_: Exception) {
                }

                // Prevent the old alarm receiver from showing it.
                reminderDao.updateReminder(
                    oldReminder.copy(
                        status = "DISMISSED"
                    )
                )

                Log.d(
                    "ONREAD_CONSISTENCY",
                    "Replaced old reminder ID ${oldReminder.id} " +
                            "for phone $phoneNumber, threadId=$threadId"
                )
            }
        }
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
        reminderDao.updateReminder(reminder)
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

            val suffix1 =
                if (digits1.length >= 10) {
                    digits1.takeLast(10)
                } else {
                    digits1
                }

            val suffix2 =
                if (digits2.length >= 10) {
                    digits2.takeLast(10)
                } else {
                    digits2
                }

            if (
                suffix1.length >= 7 &&
                suffix2.length >= 7 &&
                suffix1 == suffix2
            ) {
                return true
            }

            return digits1.length >= 7 &&
                digits2.length >= 7 &&
                (
                    digits1.endsWith(digits2) ||
                        digits2.endsWith(digits1)
                    )
        }
    }
}