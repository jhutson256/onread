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
        const val ACTION_SNOOZE = "com.example.onread.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("EXTRA_REMINDER_ID", -1L)
        Log.d(TAG, "onReceive triggered with reminderId: $reminderId, action: ${intent.action}")
        if (reminderId == -1L) {
            Log.d(TAG, "Invalid reminderId received (-1L)")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val reminderDao = database.reminderDao()
                val settingsRepository = SettingsRepository(context)

                val snoozeEnabled = settingsRepository.snoozeEnabledFlow.first()
                val snoozeDurationMinutes = settingsRepository.snoozeDurationMinutesFlow.first()
                val maxSnoozeCount = settingsRepository.maxSnoozeCountFlow.first()

                val reminder = reminderDao.getReminderById(reminderId).first()
                Log.d(TAG, "Database operation: Fetched reminder for ID $reminderId: $reminder")

                if (reminder != null && reminder.status == "PENDING") {
                    if (intent.action == ACTION_SNOOZE) {
                        if (snoozeEnabled && reminder.snoozeCount < maxSnoozeCount) {
                            val newSnoozeCount = reminder.snoozeCount + 1
                            val newTriggerTime = System.currentTimeMillis() + (snoozeDurationMinutes * 60 * 1000L)

                            val updatedReminder = reminder.copy(
                                triggerTime = newTriggerTime,
                                snoozeCount = newSnoozeCount
                            )
                            reminderDao.updateReminder(updatedReminder)
                            NotificationHelper.scheduleReminderAlarm(context, reminderId, newTriggerTime)

                            // Cancel current notification
                            try {
                                NotificationManagerCompat.from(context).cancel(reminderId.toInt())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            Log.d(TAG, "Snoozed reminder ID $reminderId to $newTriggerTime (snoozeCount: $newSnoozeCount / $maxSnoozeCount)")
                        } else {
                            Log.d(TAG, "Snooze rejected: snoozeEnabled=$snoozeEnabled, currentSnoozeCount=${reminder.snoozeCount}, maxSnoozeCount=$maxSnoozeCount")
                        }
                    } else {
                        NotificationHelper.showReminderNotification(
                            context,
                            reminder.id,
                            reminder.contactName,
                            reminder.messageBody,
                            snoozeEnabled,
                            snoozeDurationMinutes,
                            maxSnoozeCount,
                            reminder.snoozeCount
                        )
                        Log.d(TAG, "Posted notification for reminder ID $reminderId (${reminder.contactName})")
                    }
                } else {
                    Log.d(TAG, "Reminder not found or status is not PENDING (status: ${reminder?.status})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm/snooze for reminder ID $reminderId", e)
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
