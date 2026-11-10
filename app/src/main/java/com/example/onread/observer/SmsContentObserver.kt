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
import com.example.onread.data.repository.ReminderRepository
import com.example.onread.data.repository.SettingsRepository
import com.example.onread.receiver.ReminderAlarmReceiver
import com.example.onread.util.ContactSyncHelper
import com.example.onread.util.ReminderSchedulingHelper
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

        fun calculateSpecificTriggerTime(messageTimestamp: Long, targetHour: Int, targetMinute: Int): Long {
            val baseTime = if (messageTimestamp > 0) messageTimestamp else System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = baseTime
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= baseTime) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            return calendar.timeInMillis
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
            val stripped = address.replace(Regex("(?i)insert-address-token"), "").trim()
            if (stripped.isBlank()) return null
            if (stripped.contains("@")) {
                return stripped
            }
            val hasLeadingPlus = stripped.startsWith("+")
            val digitsOnly = stripped.filter { it.isDigit() }
            if (digitsOnly.isEmpty()) return null
            return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
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

        fun isNewMessage(
            timestamp: Long,
            observerStartTime: Long,
            messageId: Long,
            initialMaxId: Long,
            gracePeriodMs: Long = 10000L
        ): Boolean {
            return timestamp >= (observerStartTime - gracePeriodMs) && messageId > initialMaxId
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
        Log.d("ONREAD_CONSISTENCY", "ContentObserver onChange fired for URI: $uri, selfChange: $selfChange")
        pollLatestSms()
        pollLatestMms()
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d("ONREAD_CONSISTENCY", "ContentObserver onChange fired (no URI), selfChange: $selfChange")
        pollLatestSms()
        pollLatestMms()
    }

    private fun pollLatestSms() {
        scope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.d("ONREAD_CONSISTENCY", "READ_SMS permission not granted. Skipping pollLatestSms.")
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
                        Log.d("ONREAD_CONSISTENCY", "No new SMS/RCS messages to process (lastProcessedSmsId=$currentLastId)")
                        return@launch
                    }

                    Log.d("ONREAD_CONSISTENCY", "Polling content://sms: found ${it.count} new entries (lastProcessedSmsId=$currentLastId)")

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
                            Log.d("ONREAD_CONSISTENCY", "SMS Message ID $id already processed (currentLastId=$currentLastId). Skipping.")
                            continue
                        }

                        lastProcessedSmsId.updateAndGet { current -> maxOf(current, id) }

                        val dateMs = normalizeSmsTimestamp(rawDate)

                        Log.d("ONREAD_CONSISTENCY", "Detected SMS/RCS message entry: id=$id, type=$type, rawAddress='$rawAddress', threadId=$threadId, dateMs=$dateMs, body='$body'")

                        if (!isNewMessage(dateMs, observerStartTime, id, initialMaxSmsId)) {
                            Log.d("ONREAD_CONSISTENCY", "Skipping historical SMS/RCS Message ID $id (dateMs=$dateMs, observerStartTime=$observerStartTime, initialMaxSmsId=$initialMaxSmsId)")
                            continue
                        }

                        val resolvedAddress = resolveAddress(context, rawAddress, threadId)

                        if (resolvedAddress != null) {
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
                                        useSpecificTime = useSpecificTime,
                                        specificHour = specificHour,
                                        specificMinute = specificMinute,
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
                Log.d("ONREAD_CONSISTENCY", "READ_SMS permission not granted. Skipping pollLatestMms.")
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

                cursor?.use {
                    if (it.count == 0) {
                        Log.d("ONREAD_CONSISTENCY", "No new MMS messages to process (lastProcessedMmsId=$currentLastId)")
                        return@launch
                    }

                    Log.d("ONREAD_CONSISTENCY", "Polling content://mms: found ${it.count} new entries (lastProcessedMmsId=$currentLastId)")

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
                            Log.d("ONREAD_CONSISTENCY", "MMS Message ID $id already processed (currentLastId=$currentLastId). Skipping.")
                            continue
                        }

                        lastProcessedMmsId.updateAndGet { current -> maxOf(current, id) }

                        val dateMs = normalizeMmsTimestamp(rawDate)

                        Log.d("ONREAD_CONSISTENCY", "Detected MMS entry: id=$id, msgBox=$msgBox, mType=$mType, threadId=$threadId, dateMs=$dateMs")

                        if (!isNewMessage(dateMs, observerStartTime, id, initialMaxMmsId)) {
                            Log.d("ONREAD_CONSISTENCY", "Skipping historical MMS Message ID $id (dateMs=$dateMs, observerStartTime=$observerStartTime, initialMaxMmsId=$initialMaxMmsId)")
                            continue
                        }

                        val isIncoming = isIncomingMms(msgBox, mType)
                        val isOutgoing = isOutgoingMms(msgBox, mType)

                        if (isIncoming) {
                            val senders = getMmsAddresses(id, PDU_HEADER_FROM)
                            var rawSender = senders.firstOrNull()
                            Log.d("ONREAD_CONSISTENCY", "MMS id=$id PDU_HEADER_FROM (type 137) rawSender='$rawSender'")
                            if (rawSender.isNullOrBlank() && threadId != null && threadId > 0) {
                                rawSender = resolveAddress(context, null, threadId)
                                Log.d("ONREAD_CONSISTENCY", "MMS id=$id fallback resolveAddress for threadId=$threadId: rawSender='$rawSender'")
                            }
                            val resolvedSender = resolveAddress(context, rawSender, threadId)
                            if (resolvedSender != null) {
                                val body = extractMmsBody(id, subject)
                                processIncomingMessage(
                                    senderAddress = resolvedSender,
                                    body = body,
                                    timestampMs = dateMs,
                                    sourceTag = "MMS",
                                    savedContactsOnly = savedContactsOnly,
                                    defaultDelay = defaultDelay,
                                    useSpecificTime = useSpecificTime,
                                    specificHour = specificHour,
                                    specificMinute = specificMinute,
                                    defaultContactReminderEnabled = defaultContactReminderEnabled,
                                    mutableContactSettings = mutableContactSettings
                                )
                            }
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

    // Stub implementations for methods referenced in ContentObserver if defined in helper or extension functions
    private fun processIncomingMessage(
        senderAddress: String,
        body: String,
        timestampMs: Long,
        sourceTag: String,
        savedContactsOnly: Boolean,
        defaultDelay: Int,
        useSpecificTime: Boolean,
        specificHour: Int,
        specificMinute: Int,
        defaultContactReminderEnabled: Boolean,
        mutableContactSettings: MutableList<ContactSettingEntity>
    ) {
        // Implement message handling & reminder scheduling
    }

    private fun processOutgoingMessage(address: String) {
        // Implement outgoing message cleanup
    }

    private fun getMmsAddresses(mmsId: Long, pduType: Int): List<String> {
        return emptyList()
    }

    private fun extractMmsBody(mmsId: Long, subject: String?): String {
        return subject ?: "[Media]"
    }
}
