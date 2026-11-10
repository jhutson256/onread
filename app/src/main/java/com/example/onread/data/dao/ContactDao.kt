package com.example.onread.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.onread.data.entity.ContactSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact_settings ORDER BY LOWER(displayName) ASC")
    fun getAllContactSettings(): Flow<List<ContactSettingEntity>>

    @Query("SELECT * FROM contact_settings WHERE isSavedContact = 1 ORDER BY LOWER(displayName) ASC")
    fun getAllSavedContactSettings(): Flow<List<ContactSettingEntity>>

    @Query("SELECT * FROM contact_settings WHERE contactId = :contactId")
    fun getContactSettingById(contactId: String): Flow<ContactSettingEntity?>

    @Query("SELECT * FROM contact_settings WHERE phoneNumber = :phoneNumber")
    fun getContactSettingByPhoneNumber(phoneNumber: String): Flow<ContactSettingEntity?>

    @Query("SELECT * FROM contact_settings WHERE groupId = :groupId ORDER BY LOWER(displayName) ASC")
    fun getContactsByGroupId(groupId: String): Flow<List<ContactSettingEntity>>

    @Query("UPDATE contact_settings SET groupId = NULL WHERE groupId = :groupId")
    suspend fun removeGroupFromContacts(groupId: Long)

    @Query("UPDATE contact_settings SET groupId = NULL WHERE groupId = :groupId")
    suspend fun removeGroupFromContacts(groupId: String)

    @Query("UPDATE contact_settings SET isReminderEnabled = :isReminderEnabled, customDelayMinutes = :customDelayMinutes, useSpecificTime = :useSpecificTime, specificHour = :specificHour, specificMinute = :specificMinute WHERE groupId = :groupId")
    suspend fun updateContactsForGroup(
        groupId: String,
        isReminderEnabled: Boolean,
        customDelayMinutes: Int?,
        useSpecificTime: Boolean,
        specificHour: Int,
        specificMinute: Int
    )

    @Query("UPDATE contact_settings SET groupId = :groupId, isReminderEnabled = :isReminderEnabled, customDelayMinutes = :customDelayMinutes, useSpecificTime = :useSpecificTime, specificHour = :specificHour, specificMinute = :specificMinute WHERE contactId = :contactId")
    suspend fun assignContactToGroupWithSettings(
        contactId: String,
        groupId: String?,
        isReminderEnabled: Boolean,
        customDelayMinutes: Int?,
        useSpecificTime: Boolean,
        specificHour: Int,
        specificMinute: Int
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactSetting(setting: ContactSettingEntity)

    @Update
    suspend fun updateContactSetting(setting: ContactSettingEntity)

    @Query("UPDATE contact_settings SET isReminderEnabled = :enabled")
    suspend fun updateAllReminderSettings(enabled: Boolean)

    @Query("UPDATE contact_settings SET customDelayMinutes = NULL WHERE customSpecificHour IS NULL OR customSpecificMinute IS NULL")
    suspend fun clearCustomDelaysWithoutCustomTime()

    @Delete
    suspend fun deleteContactSetting(setting: ContactSettingEntity)
}
