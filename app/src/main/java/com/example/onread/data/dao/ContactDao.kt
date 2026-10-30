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

    @Query("SELECT * FROM contact_settings WHERE contactId = :contactId")
    fun getContactSettingById(contactId: String): Flow<ContactSettingEntity?>

    @Query("SELECT * FROM contact_settings WHERE phoneNumber = :phoneNumber")
    fun getContactSettingByPhoneNumber(phoneNumber: String): Flow<ContactSettingEntity?>

    @Query("UPDATE contact_settings SET groupId = NULL WHERE groupId = :groupId")
    suspend fun removeGroupFromContacts(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactSetting(setting: ContactSettingEntity)

    @Update
    suspend fun updateContactSetting(setting: ContactSettingEntity)

    @Query("UPDATE contact_settings SET isReminderEnabled = :enabled")
    suspend fun updateAllReminderSettings(enabled: Boolean)

    @Delete
    suspend fun deleteContactSetting(setting: ContactSettingEntity)
}
