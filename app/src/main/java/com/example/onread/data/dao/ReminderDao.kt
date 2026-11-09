package com.example.onread.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.onread.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY timestamp DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'PENDING'")
    suspend fun getPendingRemindersList(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status != 'PENDING' ORDER BY timestamp DESC")
    fun getHistoryReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun getReminderById(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE status = :status ORDER BY timestamp DESC")
    fun getRemindersByStatus(status: String): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE timestamp >= :cutoffTime")
    suspend fun getRemindersSince(cutoffTime: Long): List<ReminderEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM reminders 
            WHERE (phoneNumber = :phoneNumber OR (LENGTH(:phoneSuffix) >= 10 AND phoneNumber LIKE '%' || :phoneSuffix))
              AND LOWER(TRIM(messageBody)) = LOWER(TRIM(:messageBody))
              AND status = 'PENDING'
              AND timestamp >= :cutoffTime
        )
    """)
    suspend fun hasRecentPendingReminder(
        phoneNumber: String,
        phoneSuffix: String,
        messageBody: String,
        cutoffTime: Long
    ): Boolean

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("UPDATE reminders SET status = 'DISMISSED' WHERE phoneNumber = :phoneNumber AND status = 'PENDING'")
    suspend fun dismissRemindersForNumber(phoneNumber: String)
}
