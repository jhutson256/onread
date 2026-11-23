package com.example.onread.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val ACTION_SNOOZE =
            "com.example.onread.ACTION_SNOOZE"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val reminderId = intent.getLongExtra(
            "EXTRA_REMINDER_ID",
            -1L
        )

        Log.d(
            TAG,
            "onReceive triggered with reminderId: " +
                    "$reminderId, action: ${intent.action}"
        )

        if (reminderId == -1L) {
            Log.d(TAG, "Invalid reminderId received (-1L)")
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database =
                    AppDatabase.getDatabase(context)

                val reminderDao =
                    database.reminderDao()

                val settingsRepository =
                    SettingsRepository(context)

                val snoozeEnabled =
                    settingsRepository.snoozeEnabledFlow.first()

                val snoozeDurationMinutes =
                    settingsRepository.snoozeDurationMinutesFlow.first()

                val maxSnoozeCount =
                    settingsRepository.maxSnoozeCountFlow.first()

                val reminder =
                    reminderDao.getReminderById(reminderId).first()

                Log.d(
                    TAG,
                    "Fetched reminder for ID $reminderId: $reminder"
                )

                if (
                    reminder == null ||
                    reminder.status != "PENDING"
                ) {
                    Log.d(
                        TAG,
                        "Reminder not found or is no longer pending. " +
                                "Status: ${reminder?.status}"
                    )
                    return@launch
                }

                if (intent.action == ACTION_SNOOZE) {
                    if (
                        snoozeEnabled &&
                        reminder.snoozeCount < maxSnoozeCount
                    ) {
                        val newSnoozeCount =
                            reminder.snoozeCount + 1

                        val newTriggerTime =
                            System.currentTimeMillis() +
                                    (snoozeDurationMinutes * 60 * 1000L)

                        val updatedReminder = reminder.copy(
                            triggerTime = newTriggerTime,
                            snoozeCount = newSnoozeCount
                        )

                        reminderDao.updateReminder(
                            updatedReminder
                        )

                        NotificationHelper.scheduleReminderAlarm(
                            context,
                            reminderId,
                            newTriggerTime
                        )

                        try {
                            NotificationManagerCompat
                                .from(context)
                                .cancel(reminderId.toInt())
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "Failed to cancel snoozed notification",
                                e
                            )
                        }

                        Log.d(
                            TAG,
                            "Snoozed reminder ID $reminderId"
                        )
                    } else {
                        Log.d(
                            TAG,
                            "Snooze rejected"
                        )
                    }

                    return@launch
                }

                // Check the database again immediately before showing
                // the notification. This prevents a dismissed reminder
                // from being displayed if its alarm was already firing.
                val latestReminder =
                    reminderDao.getReminderById(reminderId).first()

                if (
                    latestReminder == null ||
                    latestReminder.status != "PENDING"
                ) {
                    Log.d(
                        TAG,
                        "Not showing cancelled reminder ID " +
                                "$reminderId. Status: " +
                                "${latestReminder?.status}"
                    )
                    return@launch
                }

                NotificationHelper.showReminderNotification(
                    context,
                    latestReminder.id,
                    latestReminder.contactName,
                    latestReminder.messageBody,
                    snoozeEnabled,
                    snoozeDurationMinutes,
                    maxSnoozeCount,
                    latestReminder.snoozeCount,
                    latestReminder.phoneNumber,
                    latestReminder.threadId
                )

                Log.d(
                    TAG,
                    "Posted notification for reminder ID " +
                            "$reminderId (${latestReminder.contactName}); sourceType=${latestReminder.sourceType}, " +
                            "sourceMessageId=${latestReminder.sourceMessageId}, status=${latestReminder.status}"
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error processing reminder ID $reminderId",
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
