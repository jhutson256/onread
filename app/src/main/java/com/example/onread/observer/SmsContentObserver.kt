package com.example.onread.observer

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.ContactsContract
import android.provider.Telephony
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class SmsContentObserver(
    handler: Handler,
    private val context: Context,
    private val database: AppDatabase,
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "SmsContentObserver"
        private const val RCS_LOG_TAG = "ONREAD_RCS"

        const val PDU_HEADER_FROM = 137
        const val PDU_HEADER_TO = 151
        const val MMS_TYPE_RETRIEVE_CONF = 132
        const val MMS_TYPE_SEND_REQ = 128

        fun shouldProcessSms(savedContactsOnly: Boolean, isContactSaved: Boolean): Boolean {
            return !savedContactsOnly || isContactSaved
        }

        fun isIncomingMms(msgBox: Int, mType: Int): Boolean {
            return (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX) || (mType == MMS_TYPE_RETRIEVE_CONF)
        }

        fun isOutgoingMms(msgBox: Int, mType: Int): Boolean {
            return (msgBox == Telephony.Mms.MESSAGE_BOX_SENT) || (mType == MMS_TYPE_SEND_REQ)
        }

        fun normalizeMmsTimestamp(rawDate: Long): Long {
            return when {
                rawDate <= 0 -> System.currentTimeMillis()
                rawDate < 10000000000L -> rawDate * 1000L
                else -> rawDate
            }
        }

        fun normalizeSmsTimestamp(rawDate: Long): Long {
            return when {
                rawDate <= 0 -> System.currentTimeMillis()
                rawDate < 10000000000L -> rawDate * 1000L
                else -> rawDate
            }
        }

        fun cleanMmsAddress(address: String?): String? {
            if (address.isNullOrBlank()) return null
            if (address.equals("insert-address-token", ignoreCase = true) ||
                address.contains("insert-address-token", ignoreCase = true)
            ) {
                return null
            }
            return address.trim()
        }

        fun determineMmsBody(extractedText: String?, subject: String?): String {
            if (!extractedText.isNullOrBlank()) {
                return extractedText.trim()
            }
            val cleanSubject = subject?.trim()
            if (!cleanSubject.isNullOrEmpty() &&
                !cleanSubject.equals("NoSubject", ignoreCase = true) &&
                !cleanSubject.equals("null", ignoreCase = true)
            ) {
                return cleanSubject
            }
            return "[Media / RCS message]"
        }

        fun isNewMessage(timestamp: Long, observerStartTime: Long, messageId: Long, initialMaxId: Long): Boolean {
            return timestamp >= observerStartTime && messageId > initialMaxId
        }

        fun isLikelyPhoneNumber(address: String?): Boolean {
            if (address.isNullOrBlank()) return false
            val trimmed = address.trim()
            if (trimmed.contains("@") || trimmed.startsWith("+")) return true
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length >= 5) return true
            return false
        }

        fun getCanonicalAddress(context: Context, idStr: String): String? {
            if (idStr.isBlank()) return null
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            return try {
                val id = idStr.toLongOrNull() ?: return null
                val uri = Uri.parse("content://mms-sms/canonical-addresses/$id")
                context.contentResolver.query(
                    uri,
                    arrayOf("address"),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val addrIndex = cursor.getColumnIndex("address")
                        if (addrIndex != -1) {
                            val rawAddr = cursor.getString(addrIndex)
                            cleanMmsAddress(rawAddr)
                        } else null
                    } else null
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException fetching canonical address for id $idStr", e)
                null
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching canonical address for id $idStr", e)
                null
            }
        }

        fun getAddressesForThread(context: Context, threadId: Long): List<String> {
            if (threadId <= 0) return emptyList()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                return emptyList()
            }
            val addresses = mutableListOf<String>()

            try {
                val uri = Uri.parse("content://mms-sms/conversations?simple=true")
                context.contentResolver.query(
                    uri,
                    arrayOf("recipient_ids"),
                    "_id = ?",
                    arrayOf(threadId.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val recipientIdsIndex = cursor.getColumnIndex("recipient_ids")
                        if (recipientIdsIndex != -1) {
                            val recipientIdsStr = cursor.getString(recipientIdsIndex)
                            if (!recipientIdsStr.isNullOrBlank()) {
                                val ids = recipientIdsStr.split(" ", ",")
                                for (id in ids) {
                                    val trimmedId = id.trim()
                                    if (trimmedId.isNotEmpty()) {
                                        val addr = getCanonicalAddress(context, trimmedId)
                                        if (!addr.isNullOrBlank()) {
                                            addresses.add(addr)
                                        } else {
                                            cleanMmsAddress(trimmedId)?.let { cleanedId ->
                                                if (isLikelyPhoneNumber(cleanedId)) {
                                                    addresses.add(cleanedId)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException fetching addresses for threadId $threadId", e)
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching addresses for threadId $threadId", e)
            }

            if (addresses.isEmpty()) {
                try {
                    val threadUri = Uri.parse("content://mms-sms/conversations/$threadId/recipients")
                    context.contentResolver.query(
                        threadUri,
                        arrayOf("address"),
                        null, null, null
                    )?.use { cursor ->
                        val addrIndex = cursor.getColumnIndex("address")
                        while (cursor.moveToNext()) {
                            if (addrIndex != -1) {
                                val rawAddr = cursor.getString(addrIndex)
                                cleanMmsAddress(rawAddr)?.let { cleaned ->
                                    val canonical = if (cleaned.all { it.isDigit() }) getCanonicalAddress(context, cleaned) else null
                                    addresses.add(canonical ?: cleaned)
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException fetching thread recipients for threadId $threadId", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching thread recipients for threadId $threadId", e)
                }
            }

            if (addresses.isEmpty()) {
                try {
                    val smsUri = Uri.parse("content://sms")
                    context.contentResolver.query(
                        smsUri,
                        arrayOf("address"),
                        "thread_id = ? AND address IS NOT NULL AND address != ''",
                        arrayOf(threadId.toString()),
                        "date DESC LIMIT 5"
                    )?.use { cursor ->
                        val addrIndex = cursor.getColumnIndex("address")
                        while (cursor.moveToNext()) {
                            if (addrIndex != -1) {
                                val rawAddr = cursor.getString(addrIndex)
                                cleanMmsAddress(rawAddr)?.let { cleaned ->
                                    val canonical = if (cleaned.all { it.isDigit() }) getCanonicalAddress(context, cleaned) else null
                                    val finalAddr = canonical ?: cleaned
                                    if (isLikelyPhoneNumber(finalAddr)) {
                                        addresses.add(finalAddr)
                                        return@use
                                    }
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException querying content://sms for threadId $threadId", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying content://sms for threadId $threadId", e)
                }
            }

            return addresses
        }

        fun resolveAddress(context: Context, rawAddress: String?, threadId: Long?): String? {
            val cleaned = cleanMmsAddress(rawAddress)

            if (!cleaned.isNullOrBlank()) {
                if (cleaned.all { it.isDigit() }) {
                    val canonical = getCanonicalAddress(context, cleaned)
                    if (!canonical.isNullOrBlank()) {
                        return canonical
                    }
                    if (isLikelyPhoneNumber(cleaned)) {
                        return cleaned
                    }
                } else {
                    return cleaned
                }
            }

            val effectiveThreadId = threadId ?: cleaned?.toLongOrNull()
            if (effectiveThreadId != null && effectiveThreadId > 0) {
                val threadAddresses = getAddressesForThread(context, effectiveThreadId)
                if (threadAddresses.isNotEmpty()) {
                    return threadAddresses.first()
                }
            }

            return cleaned?.ifEmpty { null }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val lastProcessedSmsId = AtomicLong(-1L)
    private val lastProcessedMmsId = AtomicLong(-1L)

    @Volatile
    var initialMaxSmsId: Long = -1L
        private set

    @Volatile
    var initialMaxMmsId: Long = -1L
        private set

    val observerStartTime: Long = System.currentTimeMillis()

    init {
        scope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_SMS permission not granted. Skipping initial max ID initialization.")
                return@launch
            }
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
                            val maxId = it.getLong(idIndex)
                            initialMaxSmsId = maxId
                            lastProcessedSmsId.set(maxId)
                        }
                    }
                }
                Log.d(TAG, "Initialized initialMaxSmsId=$initialMaxSmsId, lastProcessedSmsId=${lastProcessedSmsId.get()}, observerStartTime=$observerStartTime")
                Log.d(RCS_LOG_TAG, "Initialized SMS/RCS observer: initialMaxSmsId=$initialMaxSmsId, observerStartTime=$observerStartTime")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException initializing last processed SMS pointer", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing last processed SMS pointer", e)
            }

            try {
                val mmsCursor = context.contentResolver.query(
                    Uri.parse("content://mms"),
                    arrayOf(Telephony.Mms._ID),
                    null, null,
                    "_id DESC LIMIT 1",
                )
                mmsCursor?.use {
                    if (it.moveToFirst()) {
                        val idIndex = it.getColumnIndex(Telephony.Mms._ID)
                        if (idIndex != -1) {
                            val maxId = it.getLong(idIndex)
                            initialMaxMmsId = maxId
                            lastProcessedMmsId.set(maxId)
                        }
                    }
                }
                Log.d(TAG, "Initialized initialMaxMmsId=$initialMaxMmsId, lastProcessedMmsId=${lastProcessedMmsId.get()}, observerStartTime=$observerStartTime")
                Log.d(RCS_LOG_TAG, "Initialized MMS observer: initialMaxMmsId=$initialMaxMmsId, observerStartTime=$observerStartTime")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException initializing last processed MMS pointer", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing last processed MMS pointer", e)
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(RCS_LOG_TAG, "ContentObserver onChange fired for URI: $uri, selfChange: $selfChange")
        pollLatestSms()
        pollLatestMms()
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d(RCS_LOG_TAG, "ContentObserver onChange fired (no URI), selfChange: $selfChange")
        pollLatestSms()
        pollLatestMms()
    }

    private fun pollLatestSms() {
        scope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_SMS permission not granted. Skipping pollLatestSms.")
                return@launch
            }
            try {
                if (lastProcessedSmsId.get() == -1L) {
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
                                    val maxId = it.getLong(idIndex)
                                    initialMaxSmsId = maxId
                                    lastProcessedSmsId.set(maxId)
                                }
                            } else {
                                initialMaxSmsId = 0L
                                lastProcessedSmsId.set(0L)
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException initializing last processed SMS pointer", e)
                        return@launch
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing last processed SMS pointer", e)
                        return@launch
                    }
                }

                val currentLastId = lastProcessedSmsId.get()
                val cursor = context.contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.THREAD_ID
                    ),
                    "_id > ?",
                    arrayOf(currentLastId.toString()),
                    "_id ASC"
                )

                cursor?.use {
                    if (it.count == 0) {
                        Log.d(TAG, "No new SMS/RCS messages to process (lastProcessedSmsId=$currentLastId)")
                        return@launch
                    }

                    Log.d(RCS_LOG_TAG, "Polling content://sms: found ${it.count} new entries (lastProcessedSmsId=$currentLastId)")

                    val contactDao = database.contactDao()
                    val settingsRepository = SettingsRepository(context)

                    val savedContactsOnly = settingsRepository.savedContactsOnlyFlow.first()
                    val defaultDelay = settingsRepository.defaultDelayMinutesFlow.first()
                    val useSpecificTime = settingsRepository.useSpecificTimeFlow.first()
                    val specificHour = settingsRepository.specificTimeHourFlow.first()
                    val specificMinute = settingsRepository.specificTimeMinuteFlow.first()
                    val defaultContactReminderEnabled = settingsRepository.defaultContactReminderEnabled.first()
                    val mutableContactSettings = contactDao.getAllContactSettings().first().toMutableList()

                    while (it.moveToNext()) {
                        val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                        val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                        val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                        val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)

                        val id = if (idIndex != -1) it.getLong(idIndex) else 0L
                        val rawAddress = if (addressIndex != -1) it.getString(addressIndex) else null
                        val body = if (bodyIndex != -1) it.getString(bodyIndex) else null
                        val rawDate = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                        val type = if (typeIndex != -1) it.getInt(typeIndex) else 1
                        val threadId = if (threadIdIndex != -1) it.getLong(threadIdIndex) else null

                        if (id <= currentLastId) {
                            Log.d(TAG, "SMS Message ID $id already processed (currentLastId=$currentLastId). Skipping.")
                            continue
                        }

                        lastProcessedSmsId.updateAndGet { current -> maxOf(current, id) }

                        val dateMs = normalizeSmsTimestamp(rawDate)

                        Log.d(RCS_LOG_TAG, "Detected SMS/RCS message entry: id=$id, type=$type, rawAddress='$rawAddress', threadId=$threadId, dateMs=$dateMs, body='$body'")

                        if (!isNewMessage(dateMs, observerStartTime, id, initialMaxSmsId)) {
                            Log.d(RCS_LOG_TAG, "Skipping historical SMS/RCS Message ID $id (dateMs=$dateMs, observerStartTime=$observerStartTime, initialMaxSmsId=$initialMaxSmsId)")
                            continue
                        }

                        val resolvedAddress = resolveAddress(context, rawAddress, threadId)

                        if (resolvedAddress != null) {
                            // DO NOT filter out messages where protocol is null or 0 (RCS written by Google Messages often has protocol == null)
                            when (type) {
                                Telephony.Sms.MESSAGE_TYPE_INBOX -> {
                                    Log.d(RCS_LOG_TAG, "Processing INCOMING SMS/RCS from $resolvedAddress (id: $id, date: $dateMs)")
                                    processIncomingMessage(
                                        senderAddress = resolvedAddress,
                                        body = body ?: "",
                                        timestampMs = dateMs,
                                        sourceTag = "RCS/SMS",
                                        savedContactsOnly = savedContactsOnly,
                                        defaultDelay = defaultDelay,
                                        defaultContactReminderEnabled = defaultContactReminderEnabled,
                                        mutableContactSettings = mutableContactSettings
                                    )
                                }
                                Telephony.Sms.MESSAGE_TYPE_SENT -> {
                                    Log.d(RCS_LOG_TAG, "Processing OUTGOING SMS/RCS to $resolvedAddress (id: $id)")
                                    processOutgoingMessage(resolvedAddress)
                                }
                                else -> {
                                    Log.d(RCS_LOG_TAG, "Unhandled SMS/RCS type $type for message $id")
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException polling SMS in ContentObserver", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error polling SMS in ContentObserver", e)
            }
        }
    }

    private fun pollLatestMms() {
        scope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_SMS permission not granted. Skipping pollLatestMms.")
                return@launch
            }
            try {
                if (lastProcessedMmsId.get() == -1L) {
                    try {
                        val mmsCursor = context.contentResolver.query(
                            Uri.parse("content://mms"),
                            arrayOf(Telephony.Mms._ID),
                            null, null,
                            "_id DESC LIMIT 1"
                        )
                        mmsCursor?.use {
                            if (it.moveToFirst()) {
                                val idIndex = it.getColumnIndex(Telephony.Mms._ID)
                                if (idIndex != -1) {
                                    val maxId = it.getLong(idIndex)
                                    initialMaxMmsId = maxId
                                    lastProcessedMmsId.set(maxId)
                                }
                            } else {
                                initialMaxMmsId = 0L
                                lastProcessedMmsId.set(0L)
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException initializing last processed MMS pointer", e)
                        return@launch
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing last processed MMS pointer", e)
                        return@launch
                    }
                }

                val currentLastId = lastProcessedMmsId.get()
                val cursor = context.contentResolver.query(
                    Uri.parse("content://mms"),
                    arrayOf(
                        Telephony.Mms._ID,
                        Telephony.Mms.MESSAGE_BOX,
                        "m_type",
                        Telephony.Mms.DATE,
                        Telephony.Mms.SUBJECT,
                        Telephony.Mms.THREAD_ID
                    ),
                    "_id > ?",
                    arrayOf(currentLastId.toString()),
                    "_id ASC"
                )

                                    val contactId = "sms_${System.currentTimeMillis()}_${normalizedAddress.takeLast(6)}"
                                    val displayName = getContactName(context, address)
                                    contactSetting = ContactSettingEntity(
                                        contactId = contactId,
                                        phoneNumber = normalizedAddress.ifEmpty { address },
                                        displayName = displayName,
                                        isReminderEnabled = true,
                                        customDelayMinutes = null,
                                        customSpecificHour = null,
                                        customSpecificMinute = null
                                    )
                                    contactDao.insertContactSetting(contactSetting)
                                    mutableContactSettings.add(contactSetting)
                                    Log.d(TAG, "Auto-inserted default ContactSettingEntity for $address ($displayName)")
                                }
                cursor?.use {
                    if (it.count == 0) {
                        Log.d(TAG, "No new MMS messages to process (lastProcessedMmsId=$currentLastId)")
                        return@launch
                    }

                    Log.d(RCS_LOG_TAG, "Polling content://mms: found ${it.count} new entries (lastProcessedMmsId=$currentLastId)")

                                val triggerTime = if (useSpecificTime) {
                                    val targetHour = contactSetting.customSpecificHour ?: specificHour
                                    val targetMinute = contactSetting.customSpecificMinute ?: specificMinute
                                    val calendar = java.util.Calendar.getInstance().apply {
                                        timeInMillis = date
                                        set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                                        set(java.util.Calendar.MINUTE, targetMinute)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                        if (timeInMillis <= date) {
                                            add(java.util.Calendar.DAY_OF_YEAR, 1)
                                        }
                                    }
                                    calendar.timeInMillis
                                } else {
                                    val delayMinutes = contactSetting.customDelayMinutes ?: defaultDelay
                                    date + (delayMinutes * 60 * 1000L)
                                }
                    val contactDao = database.contactDao()
                    val settingsRepository = SettingsRepository(context)

                    val savedContactsOnly = settingsRepository.savedContactsOnlyFlow.first()
                    val defaultDelay = settingsRepository.defaultDelayMinutesFlow.first()
                    val defaultContactReminderEnabled = settingsRepository.defaultContactReminderEnabled.first()
                    val mutableContactSettings = contactDao.getAllContactSettings().first().toMutableList()

                    while (it.moveToNext()) {
                        val idIndex = it.getColumnIndex(Telephony.Mms._ID)
                        val msgBoxIndex = it.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                        val mTypeIndex = it.getColumnIndex("m_type")
                        val dateIndex = it.getColumnIndex(Telephony.Mms.DATE)
                        val subIndex = it.getColumnIndex(Telephony.Mms.SUBJECT)
                        val threadIdIndex = it.getColumnIndex(Telephony.Mms.THREAD_ID)

                        val id = if (idIndex != -1) it.getLong(idIndex) else 0L
                        val msgBox = if (msgBoxIndex != -1) it.getInt(msgBoxIndex) else 0
                        val mType = if (mTypeIndex != -1) it.getInt(mTypeIndex) else 0
                        val rawDate = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                        val subject = if (subIndex != -1) it.getString(subIndex) else null
                        val threadId = if (threadIdIndex != -1) it.getLong(threadIdIndex) else null

                        if (id <= currentLastId) {
                            Log.d(TAG, "MMS Message ID $id already processed (currentLastId=$currentLastId). Skipping.")
                            continue
                        }

                        lastProcessedMmsId.updateAndGet { current -> maxOf(current, id) }

                        val dateMs = normalizeMmsTimestamp(rawDate)

                        Log.d(RCS_LOG_TAG, "Detected MMS entry: id=$id, msgBox=$msgBox, mType=$mType, threadId=$threadId, dateMs=$dateMs")

                        if (!isNewMessage(dateMs, observerStartTime, id, initialMaxMmsId)) {
                            Log.d(RCS_LOG_TAG, "Skipping historical MMS Message ID $id (dateMs=$dateMs, observerStartTime=$observerStartTime, initialMaxMmsId=$initialMaxMmsId)")
                            continue
                        }

                        val isIncoming = isIncomingMms(msgBox, mType)
                        val isOutgoing = isOutgoingMms(msgBox, mType)

                        if (isIncoming) {
                            val senders = getMmsAddresses(id, PDU_HEADER_FROM)
                            var senderAddress = senders.firstOrNull()
                            if (senderAddress.isNullOrBlank() && threadId != null && threadId > 0) {
                                val threadAddrs = getAddressesForThread(context, threadId)
                                senderAddress = threadAddrs.firstOrNull()
                            }
                            val resolvedSender = senderAddress?.let { resolveAddress(context, it, threadId) }
                            if (resolvedSender != null) {
                                val body = extractMmsBody(id, subject)
                                processIncomingMessage(
                                    senderAddress = resolvedSender,
                                    body = body,
                                    timestampMs = dateMs,
                                    sourceTag = "MMS",
                                    savedContactsOnly = savedContactsOnly,
                                    defaultDelay = defaultDelay,
                                    defaultContactReminderEnabled = defaultContactReminderEnabled,
                                    mutableContactSettings = mutableContactSettings
                                )
                            }
                        } else if (isOutgoing) {
                            var recipients = getMmsAddresses(id, PDU_HEADER_TO)
                            if (recipients.isEmpty() && threadId != null && threadId > 0) {
                                recipients = getAddressesForThread(context, threadId)
                            }
                            val resolvedRecipients = recipients.mapNotNull { recipient -> resolveAddress(context, recipient, threadId) }.distinct()
                            Log.d(RCS_LOG_TAG, "Processing OUTGOING MMS (id: $id) to recipients: $resolvedRecipients")
                            for (recipient in resolvedRecipients) {
                                processOutgoingMessage(recipient)
                            }
                        } else {
                            Log.d(RCS_LOG_TAG, "Unhandled MMS msg_box $msgBox, m_type $mType for message $id")
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException polling MMS in ContentObserver", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error polling MMS in ContentObserver", e)
            }
        }
    }

    private suspend fun processIncomingMessage(
        senderAddress: String,
        body: String,
        timestampMs: Long,
        sourceTag: String,
        savedContactsOnly: Boolean,
        defaultDelay: Int,
        defaultContactReminderEnabled: Boolean,
        mutableContactSettings: MutableList<ContactSettingEntity>
    ) {
        val reminderDao = database.reminderDao()
        val contactDao = database.contactDao()

        val isSaved = if (savedContactsOnly) ContactSyncHelper.isContactSaved(context, senderAddress) else true
        if (!shouldProcessSms(savedContactsOnly, isSaved)) {
            Log.d(RCS_LOG_TAG, "Skipping incoming $sourceTag from $senderAddress: savedContactsOnly is true and contact is not saved in contacts.")
            return
        }

        val normalizedAddress = senderAddress.replace(Regex("[^0-9+]"), "")
        val inputDigits = senderAddress.replace(Regex("[^0-9]"), "")

        var contactSetting = mutableContactSettings.find { setting ->
            val settingDigits = setting.phoneNumber.replace(Regex("[^0-9]"), "")
            (settingDigits == inputDigits) ||
                (inputDigits.length >= 10 && settingDigits.length >= 10 && inputDigits.takeLast(10) == settingDigits.takeLast(10)) ||
                (inputDigits.isNotEmpty() && settingDigits.isNotEmpty() && (inputDigits.endsWith(settingDigits) || settingDigits.endsWith(inputDigits)))
        }

        if (contactSetting == null) {
            val contactId = "${sourceTag.lowercase().replace('/', '_')}_${System.currentTimeMillis()}_${normalizedAddress.takeLast(6)}"
            val displayName = getContactName(context, senderAddress)
            contactSetting = ContactSettingEntity(
                contactId = contactId,
                phoneNumber = normalizedAddress.ifEmpty { senderAddress },
                displayName = displayName,
                isReminderEnabled = defaultContactReminderEnabled,
                customDelayMinutes = null
            )
            contactDao.insertContactSetting(contactSetting)
            mutableContactSettings.add(contactSetting)
            Log.d(RCS_LOG_TAG, "Auto-inserted default ContactSettingEntity for $senderAddress ($displayName)")
        }

        if (!contactSetting.isReminderEnabled) {
            Log.d(RCS_LOG_TAG, "Skipping incoming $sourceTag from $senderAddress: reminders disabled for contact ${contactSetting.displayName}")
            return
        }

        val delayMinutes = contactSetting.customDelayMinutes ?: defaultDelay
        val triggerTime = timestampMs + (delayMinutes * 60 * 1000L)

        val messageText = if (body.isNotBlank()) body else "[RCS message]"

        val reminder = ReminderEntity(
            phoneNumber = senderAddress,
            contactName = contactSetting.displayName,
            messageBody = messageText,
            timestamp = timestampMs,
            triggerTime = triggerTime,
            status = "PENDING"
        )
        val reminderId = reminderDao.insertReminder(reminder)
        Log.d(RCS_LOG_TAG, "Inserted ReminderEntity ID $reminderId for ${contactSetting.displayName} ($senderAddress), triggerTime: $triggerTime")

        scheduleReminderAlarm(context, reminderId, triggerTime)
    }

    private suspend fun processOutgoingMessage(recipientAddress: String) {
        val reminderDao = database.reminderDao()
        val normalizedAddress = recipientAddress.replace(Regex("[^0-9+]"), "")
        val inputDigits = recipientAddress.replace(Regex("[^0-9]"), "")

        reminderDao.dismissRemindersForNumber(normalizedAddress)
        reminderDao.dismissRemindersForNumber(recipientAddress)
        if (inputDigits.isNotEmpty()) {
            reminderDao.dismissRemindersForNumber(inputDigits)
            if (inputDigits.length >= 10) {
                reminderDao.dismissRemindersForNumber(inputDigits.takeLast(10))
            }
        }
        Log.d(RCS_LOG_TAG, "Dismissed pending reminders for recipient: $recipientAddress")
    }

    private fun getMmsAddresses(mmsId: Long, targetType: Int): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val addresses = mutableListOf<String>()
        try {
            val addrUri = Uri.parse("content://mms/$mmsId/addr")
            val cursor = context.contentResolver.query(
                addrUri,
                arrayOf("address", "type"),
                null, null, null
            )
            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val typeIndex = it.getColumnIndex("type")
                while (it.moveToNext()) {
                    val type = if (typeIndex != -1) it.getInt(typeIndex) else -1
                    if (type == targetType && addressIndex != -1) {
                        val rawAddr = it.getString(addressIndex)
                        cleanMmsAddress(rawAddr)?.let { cleaned ->
                            addresses.add(cleaned)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException fetching MMS addresses for mmsId=$mmsId, targetType=$targetType", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching MMS addresses for mmsId=$mmsId, targetType=$targetType", e)
        }
        return addresses
    }

    private fun extractMmsBody(mmsId: Long, subject: String?): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return determineMmsBody(null, subject)
        }
        var extractedText: String? = null
        try {
            val partUri = Uri.parse("content://mms/$mmsId/part")
            var cursor = context.contentResolver.query(
                partUri,
                arrayOf("_id", "ct", "text"),
                null, null, null
            )

            if (cursor == null || cursor.count == 0) {
                cursor?.close()
                cursor = context.contentResolver.query(
                    Uri.parse("content://mms/part"),
                    arrayOf("_id", "ct", "text"),
                    "mid = ?",
                    arrayOf(mmsId.toString()),
                    null
                )
            }

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val ctIndex = it.getColumnIndex("ct")
                val textIndex = it.getColumnIndex("text")

                val textParts = mutableListOf<String>()

                while (it.moveToNext()) {
                    val ct = if (ctIndex != -1) it.getString(ctIndex) else null
                    if (ct != null && ct.contains("text", ignoreCase = true)) {
                        val text = if (textIndex != -1) it.getString(textIndex) else null
                        if (!text.isNullOrBlank()) {
                            textParts.add(text)
                        } else if (idIndex != -1) {
                            val partId = it.getLong(idIndex)
                            val partDataUri = Uri.parse("content://mms/part/$partId")
                            try {
                                val streamText = context.contentResolver.openInputStream(partDataUri)?.use { stream ->
                                    stream.bufferedReader().use { reader -> reader.readText() }
                                }
                                if (!streamText.isNullOrBlank()) {
                                    textParts.add(streamText)
                                }
                            } catch (e: SecurityException) {
                                Log.w(TAG, "SecurityException reading text part stream for part $partId", e)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to read text part stream for part $partId", e)
                            }
                        }
                    }
                }
                if (textParts.isNotEmpty()) {
                    extractedText = textParts.joinToString("\n")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException extracting MMS body for message $mmsId", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting MMS body for message $mmsId", e)
        }

        return determineMmsBody(extractedText, subject)
    }

    private fun getContactName(context: Context, phoneNumber: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return phoneNumber
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
                        return cursor.getString(nameIndex) ?: phoneNumber
                    }
                }
            }
            phoneNumber
        } catch (_: SecurityException) {
            phoneNumber
        } catch (_: Exception) {
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
            Log.d(RCS_LOG_TAG, "Scheduled alarm for reminder ID $reminderId at triggerTime $triggerTime")
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
