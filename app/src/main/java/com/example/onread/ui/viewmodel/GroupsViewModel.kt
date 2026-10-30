package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactGroupEntity
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.repository.ContactGroupRepository
import com.example.onread.data.repository.ContactRepository
import com.example.onread.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class GroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val groupRepository = ContactGroupRepository(database.contactGroupDao())
    private val contactRepository = ContactRepository(database.contactDao())
    private val settingsRepository = SettingsRepository(application)

    val groups: StateFlow<List<ContactGroupEntity>> = groupRepository.allGroups
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactSettings: StateFlow<List<ContactSettingEntity>> = contactRepository.allContactSettings
        .map { list -> list.sortedBy { it.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultDelayMinutes: StateFlow<Int> = settingsRepository.defaultDelayMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    fun createGroup(name: String, isReminderEnabled: Boolean, delayMinutes: Int) {
        viewModelScope.launch {
            val groupId = UUID.randomUUID().toString()
            val group = ContactGroupEntity(
                groupId = groupId,
                name = name,
                isReminderEnabled = isReminderEnabled,
                delayMinutes = delayMinutes
            )
            groupRepository.insertOrUpdateGroup(group)
        }
    }

    fun updateGroup(group: ContactGroupEntity) {
        viewModelScope.launch {
            groupRepository.insertOrUpdateGroup(group)
        }
    }

    fun deleteGroup(group: ContactGroupEntity) {
        viewModelScope.launch {
            database.contactDao().removeGroupFromContacts(group.groupId)
            groupRepository.deleteGroup(group)
        }
    }

    fun assignContactToGroup(contactId: String, groupId: String?) {
        viewModelScope.launch {
            val contact = database.contactDao().getContactSettingById(contactId).first()
            if (contact != null) {
                contactRepository.insertOrUpdateContactSetting(contact.copy(groupId = groupId))
            }
        }
    }

    fun updateContactSetting(setting: ContactSettingEntity) {
        viewModelScope.launch {
            contactRepository.insertOrUpdateContactSetting(setting)
        }
    }
}
