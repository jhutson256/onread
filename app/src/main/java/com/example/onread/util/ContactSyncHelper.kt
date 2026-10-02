package com.example.onread.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.example.onread.data.dao.ContactDao
import com.example.onread.data.entity.ContactSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object ContactSyncHelper {

    suspend fun syncContacts(context: Context, contactDao: ContactDao) {
        withContext(Dispatchers.IO) {
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
                            isReminderEnabled = existing?.isReminderEnabled ?: true,
                            customDelayMinutes = existing?.customDelayMinutes
                        )
                        contactDao.insertContactSetting(entity)
                    }
                }
            }
        }
    }

    fun isContactSaved(context: Context, phoneNumber: String): Boolean {
        val normalizedInput = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (normalizedInput.isEmpty()) return false

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.count > 0) return true
        }
        return false
    }
}
