package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactGroupEntity
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.repository.ContactGroupRepository
import com.example.onread.data.repository.ContactRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val groupRepository = ContactGroupRepository(database.contactGroupDao(), database.contactDao())
    private val contactRepository = ContactRepository(database.contactDao())

    val groups: StateFlow<List<ContactGroupEntity>> = groupRepository.allGroups
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactSettings: StateFlow<List<ContactSettingEntity>> = contactRepository.allContactSettings
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addGroup(name: String, isReminderEnabled: Boolean, customDelayMinutes: Int) {
        viewModelScope.launch {
            groupRepository.insertGroup(
                ContactGroupEntity(
                    name = name,
                    isReminderEnabled = isReminderEnabled,
                    customDelayMinutes = customDelayMinutes
                )
            )
        }
    }

    fun updateGroup(group: ContactGroupEntity) {
        viewModelScope.launch {
            groupRepository.updateGroup(group)
        }
    }

    fun deleteGroup(group: ContactGroupEntity) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
        }
    }

    fun assignContactToGroup(contactId: String, groupId: Long?) {
        viewModelScope.launch {
            val contact = database.contactDao().getContactSettingById(contactId).first()
            if (contact != null) {
                val groupChanged = contact.groupId != groupId
                val now = System.currentTimeMillis()
                contactRepository.insertOrUpdateContactSetting(
                    contact.copy(
                        groupId = groupId,
                        groupAssignedAt = if (groupChanged) now else contact.groupAssignedAt
                    )
                )
            }
        }
    }
}
