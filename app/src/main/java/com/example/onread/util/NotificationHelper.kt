package com.example.onread.util

import android.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.onread.MainActivity
import com.example.onread.receiver.ReminderAlarmReceiver

object NotificationHelper {
    const val CHANNEL_ID = "text_reminders_channel"
    private const val CHANNEL_NAME = "Text Reminders"
    private const val CHANNEL_DESCRIPTION = "Reminders to reply to text messages"
    private const val TAG = "NotificationHelper"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(
        context: Context,
        reminderId: Long,
        contactName: String,
        messageBody: String,
        snoozeEnabled: Boolean = true,
        snoozeDurationMinutes: Int = 15,
        maxSnoozeCount: Int = 3,
        currentSnoozeCount: Int = 0
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_DASHBOARD", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_email)
            .setContentTitle("Unreplied text from $contactName")
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (snoozeEnabled && currentSnoozeCount < maxSnoozeCount) {
            val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_SNOOZE
                putExtra("EXTRA_REMINDER_ID", reminderId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId + 100000).toInt(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Snooze (${snoozeDurationMinutes}m)", snoozePendingIntent)
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(reminderId.toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun scheduleReminderAlarm(context: Context, reminderId: Long, triggerTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra("EXTRA_REMINDER_ID", reminderId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d(TAG, "Scheduled exact alarm for reminder ID $reminderId at $triggerTime")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException setting exact alarm, falling back to standard alarm", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule alarm for reminder ID $reminderId", ex)
            }
        }
    }
}
