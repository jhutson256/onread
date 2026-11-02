package com.example.onread.data.repository

import com.example.onread.data.dao.ContactDao
import com.example.onread.data.dao.GroupDao
import com.example.onread.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

class GroupRepository(
    private val groupDao: GroupDao,
    private val contactDao: ContactDao
) {
    val allGroups: Flow<List<GroupEntity>> = groupDao.getAllGroups()

    fun getGroupById(groupId: String): Flow<GroupEntity?> = groupDao.getGroupById(groupId)

    suspend fun insertOrUpdateGroup(group: GroupEntity) {
        groupDao.insertGroup(group)
        contactDao.updateContactsForGroup(
            groupId = group.id,
            isReminderEnabled = group.isReminderEnabled,
            customDelayMinutes = group.customDelayMinutes,
            useSpecificTime = group.useSpecificTime,
            specificHour = group.specificHour,
            specificMinute = group.specificMinute
        )
    }

    suspend fun updateGroup(group: GroupEntity) {
        groupDao.updateGroup(group)
        contactDao.updateContactsForGroup(
            groupId = group.id,
            isReminderEnabled = group.isReminderEnabled,
            customDelayMinutes = group.customDelayMinutes,
            useSpecificTime = group.useSpecificTime,
            specificHour = group.specificHour,
            specificMinute = group.specificMinute
        )
    }

    suspend fun deleteGroup(group: GroupEntity) {
        contactDao.removeGroupFromContacts(group.id)
        groupDao.deleteGroup(group)
    }
}
