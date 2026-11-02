package com.example.onread.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.onread.data.dao.ContactDao
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object ContactSyncHelper {

    private const val TAG = "ContactSyncHelper"

    suspend fun syncContacts(
        context: Context,
        contactDao: ContactDao,
        defaultReminderEnabled: Boolean? = null
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted. Skipping contact sync.")
            return
        }

        val defaultEnabled = defaultReminderEnabled
            ?: SettingsRepository(context).defaultContactReminderEnabled.first()

        withContext(Dispatchers.IO) {
            try {
                val existingSettings = contactDao.getAllContactSettings().first().associateBy { it.contactId }
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val contactId = if (idIndex != -1) it.getString(idIndex) else ""
                        val displayName = if (nameIndex != -1) it.getString(nameIndex) else "Unknown"
                        val phoneNumber = if (numberIndex != -1) it.getString(numberIndex) else ""

                        if (contactId.isNotEmpty() && phoneNumber.isNotEmpty()) {
                            val normalizedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                            val existing = existingSettings[contactId]
                            val entity = ContactSettingEntity(
                                contactId = contactId,
                                phoneNumber = normalizedNumber,
                                displayName = displayName,
                                isReminderEnabled = existing?.isReminderEnabled ?: defaultEnabled,
                                customDelayMinutes = existing?.customDelayMinutes,
                                customDelayUpdatedAt = existing?.customDelayUpdatedAt ?: 0L,
                                groupId = existing?.groupId,
                                groupAssignedAt = existing?.groupAssignedAt ?: 0L
                            )
                            contactDao.insertContactSetting(entity)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException during contact sync", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during contact sync", e)
            }
        }
    }

    fun isContactSaved(context: Context, phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val normalizedInput = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (normalizedInput.isEmpty()) return false

        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                it.count > 0
            } ?: false
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking if contact is saved", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking if contact is saved", e)
            false
        }
    }
}
