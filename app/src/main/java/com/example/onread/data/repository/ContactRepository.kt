package com.example.onread.data.repository

import com.example.onread.data.dao.ContactDao
import com.example.onread.data.entity.ContactSettingEntity
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {

    val allContactSettings: Flow<List<ContactSettingEntity>> = contactDao.getAllContactSettings()
    val allSavedContactSettings: Flow<List<ContactSettingEntity>> = contactDao.getAllSavedContactSettings()

    fun getContactSettingById(contactId: String): Flow<ContactSettingEntity?> {
        return contactDao.getContactSettingById(contactId)
    }

    fun getContactSettingByPhoneNumber(phoneNumber: String): Flow<ContactSettingEntity?> {
        return contactDao.getContactSettingByPhoneNumber(phoneNumber)
    }

    suspend fun insertOrUpdateContactSetting(setting: ContactSettingEntity) {
        contactDao.insertContactSetting(setting)
    }

    suspend fun updateAllReminderSettings(enabled: Boolean) {
        contactDao.updateAllReminderSettings(enabled)
    }

    suspend fun clearCustomDelaysForClockMode() {
        contactDao.clearCustomDelaysWithoutCustomTime()
    }

    suspend fun deleteContactSetting(setting: ContactSettingEntity) {
        contactDao.deleteContactSetting(setting)
    }
}
