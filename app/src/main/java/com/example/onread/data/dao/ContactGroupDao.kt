package com.example.onread.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.onread.data.entity.ContactGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactGroupDao {
    @Query("SELECT * FROM contact_groups ORDER BY LOWER(name) ASC")
    fun getAllGroups(): Flow<List<ContactGroupEntity>>

    @Query("SELECT * FROM contact_groups WHERE groupId = :groupId")
    fun getGroupById(groupId: String): Flow<ContactGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ContactGroupEntity)

    @Update
    suspend fun updateGroup(group: ContactGroupEntity)

    @Delete
    suspend fun deleteGroup(group: ContactGroupEntity)

    @Query("DELETE FROM contact_groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: String)
}
