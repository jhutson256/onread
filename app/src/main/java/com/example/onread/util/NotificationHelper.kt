package com.example.onread.util

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.onread.MainActivity
import com.example.onread.receiver.ReminderAlarmReceiver

object NotificationHelper {
    const val CHANNEL_ID = "text_reminders_channel"
    private const val CHANNEL_NAME = "Text Reminders"
    private const val CHANNEL_DESCRIPTION = "Reminders to reply to text messages"
    const val ACTION_SNOOZE = "com.example.onread.ACTION_SNOOZE"

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

    fun getMessagingAppIntent(phoneNumber: String, threadId: Long): Intent {
        return if (threadId > 0L) {
            Intent(Intent.ACTION_VIEW, Uri.parse("content://mms-sms/conversations/$threadId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun showReminderNotification(
        context: Context,
        reminderId: Long,
        contactName: String,
        messageBody: String,
        phoneNumber: String = "",
        snoozeEnabled: Boolean = true,
        snoozeDurationMinutes: Int = 15,
        maxSnoozeCount: Int = 3,
        currentSnoozeCount: Int = 0,
        phoneNumber: String = "",
        threadId: Long = 0L
    ) {
        val intent = if (phoneNumber.isNotBlank()) {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_DASHBOARD", true)
            }
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

        if (phoneNumber.isNotBlank() || threadId > 0L) {
            val chatIntent = getMessagingAppIntent(phoneNumber, threadId)
            val chatPendingIntent = PendingIntent.getActivity(
                context,
                (reminderId + 200000).toInt(),
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Open Chat", chatPendingIntent)
        }

        if (snoozeEnabled && currentSnoozeCount < maxSnoozeCount) {
            val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra("EXTRA_REMINDER_ID", reminderId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt() + 10000,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_menu_agenda,
                "Snooze (${snoozeDurationMinutes}m)",
                snoozePendingIntent
            )
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(reminderId.toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showReminderNotification(context: Context, reminderId: Long, contactName: String, messageBody: String, phoneNumber: String) {
        showReminderNotification(context, reminderId, contactName, messageBody, phoneNumber, true, 15, 3, 0)
    }

    fun cancelReminderAlarm(context: Context, reminderId: Long) {
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
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for reminder ID $reminderId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel alarm for reminder ID $reminderId", e)
        }
    }
}
