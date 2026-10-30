package com.example.onread.data.repository

import com.example.onread.data.dao.ContactDao
import com.example.onread.data.dao.ContactGroupDao
import com.example.onread.data.entity.ContactGroupEntity
import kotlinx.coroutines.flow.Flow

class ContactGroupRepository(
    private val contactGroupDao: ContactGroupDao,
    private val contactDao: ContactDao
) {
    val allGroups: Flow<List<ContactGroupEntity>> = contactGroupDao.getAllGroups()

    fun getGroupById(groupId: Long): Flow<ContactGroupEntity?> {
        return contactGroupDao.getGroupById(groupId)
    }

    suspend fun insertGroup(group: ContactGroupEntity): Long {
        return contactGroupDao.insertGroup(group)
    }

    suspend fun updateGroup(group: ContactGroupEntity) {
        contactGroupDao.updateGroup(group)
    }

    suspend fun deleteGroup(group: ContactGroupEntity) {
        contactDao.removeGroupFromContacts(group.id)
        contactGroupDao.deleteGroup(group)
    }
}
