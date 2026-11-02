package com.example.onread.data.repository

import com.example.onread.data.dao.GroupDao
import com.example.onread.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

class GroupRepository(private val groupDao: GroupDao) {
    val allGroups: Flow<List<GroupEntity>> = groupDao.getAllGroups()

    fun getGroupById(groupId: String): Flow<GroupEntity?> = groupDao.getGroupById(groupId)

    suspend fun insertOrUpdateGroup(group: GroupEntity) {
        groupDao.insertGroup(group)
    }

    suspend fun deleteGroup(group: GroupEntity) {
        groupDao.deleteGroup(group)
    }
}
