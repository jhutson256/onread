package com.example.onread.observer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.receiver.ReminderAlarmReceiver
import com.example.onread.util.ContactSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class SmsContentObserver(
    handler: Handler,
    private val context: Context,
    private val database: AppDatabase
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "SmsContentObserver"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val lastProcessedSmsId = AtomicLong(-1L)

    init {
        // Initialize lastProcessedSmsId to latest existing SMS on startup
        scope.launch {
            try {
                val smsCursor = context.contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf(Telephony.Sms._ID),
                    null, null,
                    "_id DESC LIMIT 1"
                )
                smsCursor?.use {
                    if (it.moveToFirst()) {
                        val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                        if (idIndex != -1) {
                            lastProcessedSmsId.set(it.getLong(idIndex))
                        }
                    }
                }
                Log.d(TAG, "Initialized lastProcessedSmsId=${lastProcessedSmsId.get()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing last processed SMS pointer", e)
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        pollLatestSms()
    }

    private fun pollLatestSms() {
        scope.launch {
            try {
                val currentLastId = lastProcessedSmsId.get()
                val cursor = context.contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE
                    ),
                    "_id > ?",
                    arrayOf(currentLastId.toString()),
                    "_ID ASC"
                )

                cursor?.use {
                    if (it.count == 0) {
                        Log.d(TAG, "No new SMS messages to process (lastProcessedSmsId=$currentLastId)")
                        return@launch
                    }

                    val reminderDao = database.reminderDao()
                    val contactDao = database.contactDao()
                    val settingsRepository = SettingsRepository(context)

                    val savedContactsOnly = settingsRepository.savedContactsOnlyFlow.first()
                    val defaultDelay = settingsRepository.defaultDelayMinutesFlow.first()
                    val mutableContactSettings = contactDao.getAllContactSettings().first().toMutableList()

                    while (it.moveToNext()) {
                        val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                        val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                        val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

                        val id = if (idIndex != -1) it.getLong(idIndex) else 0L
                        val address = if (addressIndex != -1) it.getString(addressIndex) else null
                        val body = if (bodyIndex != -1) it.getString(bodyIndex) else null
                        val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                        val type = if (typeIndex != -1) it.getInt(typeIndex) else 1

                        if (id <= currentLastId) {
                            Log.d(TAG, "SMS Message ID $id already processed (currentLastId=$currentLastId). Skipping.")
                            continue
                        }

                        lastProcessedSmsId.updateAndGet { maxOf(it, id) }

                        if (address.isNullOrEmpty()) {
                            Log.d(TAG, "SMS address is null or empty for message $id. Skipping.")
                            continue
                        }

                        val normalizedAddress = address.replace(Regex("[^0-9+]"), "")
                        val inputDigits = address.replace(Regex("[^0-9]"), "")

                        when (type) {
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> {
                                Log.d(TAG, "Processing INCOMING SMS from $address (id: $id, date: $date)")
                                val isSaved = ContactSyncHelper.isContactSaved(context, address)

                                var contactSetting = mutableContactSettings.find { setting ->
                                    val settingDigits = setting.phoneNumber.replace(Regex("[^0-9]"), "")
                                    settingDigits == inputDigits ||
                                        (inputDigits.length >= 10 && settingDigits.length >= 10 && inputDigits.takeLast(10) == settingDigits.takeLast(10)) ||
                                        (inputDigits.isNotEmpty() && settingDigits.isNotEmpty() && (inputDigits.endsWith(settingDigits) || settingDigits.endsWith(inputDigits)))
                                }

                                if (contactSetting == null) {
                                    if (savedContactsOnly && !isSaved) {
                                        Log.d(TAG, "Skipping incoming SMS from $address: savedContactsOnly is true and contact is not saved.")
                                        continue
                                    }

                                    val contactId = "sms_${System.currentTimeMillis()}_${normalizedAddress.takeLast(6)}"
                                    val displayName = getContactName(context, address)
                                    contactSetting = ContactSettingEntity(
                                        contactId = contactId,
                                        phoneNumber = normalizedAddress.ifEmpty { address },
                                        displayName = displayName,
                                        isReminderEnabled = true,
                                        customDelayMinutes = null
                                    )
                                    contactDao.insertContactSetting(contactSetting)
                                    mutableContactSettings.add(contactSetting)
                                    Log.d(TAG, "Auto-inserted default ContactSettingEntity for $address ($displayName)")
                                }

                                if (!contactSetting.isReminderEnabled) {
                                    Log.d(TAG, "Skipping incoming SMS from $address: reminders disabled for contact ${contactSetting.displayName}")
                                    continue
                                }

                                val delayMinutes = contactSetting.customDelayMinutes ?: defaultDelay
                                val triggerTime = date + (delayMinutes * 60 * 1000L)

                                val reminder = ReminderEntity(
                                    phoneNumber = address,
                                    contactName = contactSetting.displayName,
                                    messageBody = body ?: "",
                                    timestamp = date,
                                    triggerTime = triggerTime,
                                    status = "PENDING"
                                )
                                val reminderId = reminderDao.insertReminder(reminder)
                                Log.d(TAG, "Inserted ReminderEntity ID $reminderId for ${contactSetting.displayName} ($address), triggerTime: $triggerTime")

                                scheduleReminderAlarm(context, reminderId, triggerTime)
                            }
                            Telephony.Sms.MESSAGE_TYPE_SENT -> {
                                Log.d(TAG, "Processing OUTGOING SMS to $address (id: $id)")
                                reminderDao.dismissRemindersForNumber(normalizedAddress)
                                reminderDao.dismissRemindersForNumber(address)
                                if (inputDigits.isNotEmpty()) {
                                    reminderDao.dismissRemindersForNumber(inputDigits)
                                    if (inputDigits.length >= 10) {
                                        reminderDao.dismissRemindersForNumber(inputDigits.takeLast(10))
                                    }
                                }
                                Log.d(TAG, "Dismissed pending reminders for recipient: $address")
                            }
                            else -> {
                                Log.d(TAG, "Unhandled SMS type $type for message $id")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling SMS in ContentObserver", e)
            }
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String {
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
                        return cursor.getString(nameIndex) ?: phoneNumber
                    }
                }
            }
            phoneNumber
        } catch (e: Exception) {
            phoneNumber
        }
    }

    private fun scheduleReminderAlarm(context: Context, reminderId: Long, triggerTime: Long) {
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
