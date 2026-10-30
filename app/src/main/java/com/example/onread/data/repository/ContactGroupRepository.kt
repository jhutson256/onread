package com.example.onread.data.repository

import com.example.onread.data.dao.ContactGroupDao
import com.example.onread.data.entity.ContactGroupEntity
import kotlinx.coroutines.flow.Flow

class ContactGroupRepository(private val contactGroupDao: ContactGroupDao) {
    val allGroups: Flow<List<ContactGroupEntity>> = contactGroupDao.getAllGroups()

    fun getGroupById(groupId: String): Flow<ContactGroupEntity?> {
        return contactGroupDao.getGroupById(groupId)
    }

    suspend fun insertOrUpdateGroup(group: ContactGroupEntity) {
        contactGroupDao.insertGroup(group)
    }

    suspend fun deleteGroup(group: ContactGroupEntity) {
        contactGroupDao.deleteGroup(group)
    }

    suspend fun deleteGroupById(groupId: String) {
        contactGroupDao.deleteGroupById(groupId)
    }
}
