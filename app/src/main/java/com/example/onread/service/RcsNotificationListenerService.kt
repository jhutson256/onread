package com.example.onread.service

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.receiver.ReminderAlarmReceiver
import com.example.onread.util.ContactSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RcsNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)

        if (!isMessagingApp(packageName, defaultSmsPackage)) {
            return
        }

        val notification = sbn.notification ?: return

        if (isIgnorableNotification(sbn, notification)) {
            Log.d(TAG, "Ignoring notification from $packageName: ongoing, summary, or non-message category")
            return
        }

        val (rawTitle, text) = extractTitleAndText(notification)

        if (rawTitle.isNullOrBlank() || text.isNullOrBlank()) {
            Log.d(TAG, "Skipping notification from $packageName: title or text is null/blank")
            return
        }

        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

        serviceScope.launch {
            try {
                processRcsNotification(rawTitle, text, postTime)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing RCS notification from $packageName", e)
            }
        }
    }

    private suspend fun processRcsNotification(
        rawTitle: String,
        messageText: String,
        postTime: Long
    ) {
        val database = AppDatabase.getDatabase(applicationContext)
        val contactDao = database.contactDao()
        val reminderDao = database.reminderDao()
        val settingsRepository = SettingsRepository(applicationContext)

        val savedContactsOnly = settingsRepository.savedContactsOnlyFlow.first()
        val defaultDelay = settingsRepository.defaultDelayMinutesFlow.first()
        val defaultContactReminderEnabled = settingsRepository.defaultContactReminderEnabled.first()

        val allContacts = contactDao.getAllContactSettings().first()

        val contactSetting = resolveContactSetting(
            context = applicationContext,
            rawTitle = rawTitle,
            allContacts = allContacts,
            savedContactsOnly = savedContactsOnly,
            defaultContactReminderEnabled = defaultContactReminderEnabled
        )

        if (contactSetting == null) {
            Log.d(TAG, "Skipping notification for '$rawTitle': Could not resolve contact or rejected by savedContactsOnly")
            return
        }

        if (!contactSetting.isReminderEnabled) {
            Log.d(TAG, "Skipping notification for '${contactSetting.displayName}': Reminders disabled for contact")
            return
        }

        // Deduplication check: ignore if pending reminder exists with same text for contact within 30s
        val pendingReminders = reminderDao.getPendingReminders().first()
        val isDuplicate = pendingReminders.any { pending ->
            (pending.phoneNumber == contactSetting.phoneNumber || pending.contactName.equals(contactSetting.displayName, ignoreCase = true)) &&
                    pending.messageBody == messageText &&
                    Math.abs(pending.timestamp - postTime) < 30_000L
        }

        if (isDuplicate) {
            Log.d(TAG, "Skipping duplicate notification reminder for ${contactSetting.displayName}")
            return
        }

        // Save/insert contact if not already saved in Room DB
        if (allContacts.none { it.contactId == contactSetting.contactId }) {
            contactDao.insertContactSetting(contactSetting)
            Log.d(TAG, "Inserted new ContactSettingEntity for '${contactSetting.displayName}' (${contactSetting.phoneNumber})")
        }

        val delayMinutes = contactSetting.customDelayMinutes ?: defaultDelay
        val triggerTime = calculateTriggerTime(postTime, delayMinutes)

        val reminder = ReminderEntity(
            phoneNumber = contactSetting.phoneNumber,
            contactName = contactSetting.displayName,
            messageBody = messageText,
            timestamp = postTime,
            triggerTime = triggerTime,
            status = "PENDING"
        )

        val reminderId = reminderDao.insertReminder(reminder)
        Log.d(TAG, "Inserted ReminderEntity ID $reminderId for RCS notification from ${contactSetting.displayName}")

        scheduleReminderAlarm(applicationContext, reminderId, triggerTime)
    }

    companion object {
        private const val TAG = "RcsNotifListener"

        private val KNOWN_MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.samsung.android.mms",
            "com.android.mms",
            "com.verizon.messaging.vzmessages",
            "com.messaging.mms"
        )

        fun isMessagingApp(packageName: String?, defaultSmsPackage: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            if (!defaultSmsPackage.isNullOrBlank() && packageName.equals(defaultSmsPackage, ignoreCase = true)) {
                return true
            }
            return KNOWN_MESSAGING_PACKAGES.contains(packageName.lowercase())
        }

        fun isIgnorableNotification(sbn: StatusBarNotification, notification: Notification): Boolean {
            if (sbn.isOngoing || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
                return true
            }
            if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                return true
            }
            val extras = notification.extras
            if (extras != null && extras.getBoolean("android.isGroupSummary", false)) {
                return true
            }
            val category = notification.category
            if (category == Notification.CATEGORY_TRANSPORT ||
                category == Notification.CATEGORY_SERVICE ||
                category == Notification.CATEGORY_PROGRESS ||
                category == Notification.CATEGORY_SYSTEM ||
                category == Notification.CATEGORY_RECOMMENDATION ||
                category == Notification.CATEGORY_NAVIGATION
            ) {
                return true
            }
            return false
        }

        fun extractTitleAndText(notification: Notification): Pair<String?, String?> {
            val extras = notification.extras ?: return Pair(null, null)

            var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            if (title.isNullOrBlank()) {
                title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            }

            var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (text.isNullOrBlank()) {
                text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            }

            if (text.isNullOrBlank()) {
                @Suppress("DEPRECATION")
                val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                if (!messages.isNullOrEmpty()) {
                    val lastMsg = messages.lastOrNull()
                    if (lastMsg is Bundle) {
                        text = lastMsg.getCharSequence("text")?.toString()
                    }
                }
            }

            return Pair(title?.trim(), text?.trim())
        }

        fun calculateTriggerTime(postTime: Long, delayMinutes: Int): Long {
            val baseTime = if (postTime > 0) postTime else System.currentTimeMillis()
            return baseTime + (delayMinutes * 60 * 1000L)
        }

        fun resolveContactSetting(
            context: Context,
            rawTitle: String,
            allContacts: List<ContactSettingEntity>,
            savedContactsOnly: Boolean,
            defaultContactReminderEnabled: Boolean
        ): ContactSettingEntity? {
            val cleanedTitle = rawTitle.trim()
            if (cleanedTitle.isEmpty()) return null

            // 1. Try matching existing contact setting in Room DB by display name or phone number
            val inputDigits = cleanedTitle.replace(Regex("[^0-9]"), "")
            val matchedSetting = allContacts.find { setting ->
                setting.displayName.equals(cleanedTitle, ignoreCase = true) ||
                        (inputDigits.isNotEmpty() && setting.phoneNumber.replace(Regex("[^0-9]"), "").let { settingDigits ->
                            settingDigits.isNotEmpty() && (settingDigits == inputDigits ||
                                    (inputDigits.length >= 10 && settingDigits.length >= 10 && inputDigits.takeLast(10) == settingDigits.takeLast(10)))
                        })
            }

            if (matchedSetting != null) {
                return matchedSetting
            }

            // 2. Resolve phone number and display name for new contact
            val phoneNumber: String
            val displayName: String

            val isPhoneNum = isLikelyPhoneNumber(cleanedTitle)
            if (isPhoneNum) {
                phoneNumber = cleanedTitle.replace(Regex("[^0-9+]"), "").ifEmpty { cleanedTitle }
                displayName = getContactNameFromSystem(context, cleanedTitle) ?: cleanedTitle
            } else {
                displayName = cleanedTitle
                val phoneFromSystem = getPhoneNumberFromName(context, cleanedTitle)
                phoneNumber = phoneFromSystem ?: cleanedTitle.replace(Regex("[^0-9+]"), "").ifEmpty { cleanedTitle }
            }

            // 3. Respect savedContactsOnly
            val isSavedInSystem = ContactSyncHelper.isContactSaved(context, phoneNumber) ||
                    (getPhoneNumberFromName(context, displayName) != null)

            if (savedContactsOnly && !isSavedInSystem) {
                return null
            }

            val contactId = "rcs_notif_${System.currentTimeMillis()}_${phoneNumber.takeLast(6)}"
            return ContactSettingEntity(
                contactId = contactId,
                phoneNumber = phoneNumber,
                displayName = displayName,
                isReminderEnabled = defaultContactReminderEnabled,
                customDelayMinutes = null
            )
        }

        private fun isLikelyPhoneNumber(address: String): Boolean {
            val trimmed = address.trim()
            if (trimmed.contains("@") || trimmed.startsWith("+")) return true
            val digits = trimmed.filter { it.isDigit() }
            return digits.length >= 7
        }

        private fun getContactNameFromSystem(context: Context, phoneNumber: String): String? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            return try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)
                        } else null
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun getPhoneNumberFromName(context: Context, displayName: String): String? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            return try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf(displayName, "%$displayName%"),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIndex != -1) {
                            val rawNum = it.getString(numIndex)
                            if (!rawNum.isNullOrBlank()) {
                                return rawNum.replace(Regex("[^0-9+]"), "")
                            }
                        }
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }

        private fun scheduleReminderAlarm(context: Context, reminderId: Long, triggerTime: Long) {
            val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
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
}
