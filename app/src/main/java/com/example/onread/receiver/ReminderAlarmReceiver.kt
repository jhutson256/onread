package com.example.onread.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.util.NotificationHelper
import com.example.onread.util.NotificationHelper.ACTION_SNOOZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("EXTRA_REMINDER_ID", -1L)
        Log.d(TAG, "onReceive triggered with reminderId: $reminderId")
        if (reminderId == -1L) {
            Log.d(TAG, "Invalid reminderId received (-1L)")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val reminderDao = database.reminderDao()
                val reminder = reminderDao.getReminderById(reminderId).first()
                Log.d(TAG, "Database operation: Fetched reminder for ID $reminderId: $reminder")

                if (reminder != null && reminder.status == "PENDING") {
                    val settingsRepository = SettingsRepository(context)
                    val snoozeEnabled = settingsRepository.snoozeEnabledFlow.first()
                    val snoozeDurationMinutes = settingsRepository.snoozeDurationMinutesFlow.first()
                    val maxSnoozeCount = settingsRepository.maxSnoozeCountFlow.first()

                    if (intent.action == ACTION_SNOOZE) {
                        if (snoozeEnabled && reminder.snoozeCount < maxSnoozeCount) {
                            val newSnoozeCount = reminder.snoozeCount + 1
                            val newTriggerTime = System.currentTimeMillis() + (snoozeDurationMinutes * 60 * 1000L)

                            val updatedReminder = reminder.copy(
                                triggerTime = newTriggerTime,
                                snoozeCount = newSnoozeCount
                            )
                            reminderDao.updateReminder(updatedReminder)

                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                            val alarmIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                                putExtra("EXTRA_REMINDER_ID", reminderId)
                            }
                            val pendingIntent = android.app.PendingIntent.getBroadcast(
                                context,
                                reminderId.toInt(),
                                alarmIntent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (alarmManager?.canScheduleExactAlarms() == true) {
                                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, newTriggerTime, pendingIntent)
                                } else {
                                    alarmManager?.set(android.app.AlarmManager.RTC_WAKEUP, newTriggerTime, pendingIntent)
                                }
                            } else {
                                alarmManager?.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, newTriggerTime, pendingIntent)
                            }

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
                            reminder.phoneNumber,
                            snoozeEnabled,
                            snoozeDurationMinutes,
                            maxSnoozeCount,
                            reminder.snoozeCount,
                            reminder.phoneNumber,
                            reminder.threadId
                        )
                        Log.d(TAG, "Posted notification for reminder ID $reminderId (${reminder.contactName})")
                    }
                } else {
                    Log.d(TAG, "Reminder not found or status is not PENDING (status: ${reminder?.status})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm for reminder ID $reminderId", e)
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
