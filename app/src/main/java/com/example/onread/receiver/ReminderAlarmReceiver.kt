package com.example.onread.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.onread.data.database.AppDatabase
import com.example.onread.util.NotificationHelper
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
                    NotificationHelper.showReminderNotification(
                        context,
                        reminder.id,
                        reminder.contactName,
                        reminder.messageBody,
                        reminder.phoneNumber
                    )
                    Log.d(TAG, "Posted notification for reminder ID $reminderId (${reminder.contactName})")
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
