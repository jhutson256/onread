package com.example.onread.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onread.data.database.AppDatabase
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.GroupEntity
import com.example.onread.data.repository.ContactRepository
import com.example.onread.data.repository.GroupRepository
import com.example.onread.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val groupRepository = GroupRepository(database.groupDao())
    private val contactRepository = ContactRepository(database.contactDao())

    val groups: StateFlow<List<GroupEntity>> = groupRepository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactSettingEntity>> = contactRepository.allContactSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultDelayMinutes: StateFlow<Int> = SettingsRepository(application).defaultDelayMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    fun insertOrUpdateGroup(group: GroupEntity) {
        viewModelScope.launch {
            groupRepository.insertOrUpdateGroup(group)
        }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch {
            val contactList = contactRepository.allContactSettings.first()
            for (contact in contactList) {
                if (contact.groupId == group.id) {
                    contactRepository.insertOrUpdateContactSetting(contact.copy(groupId = null))
                }
            }
            groupRepository.deleteGroup(group)
        }
    }

    fun assignContactToGroup(contactId: String, groupId: String?) {
        viewModelScope.launch {
            val contact = contactRepository.getContactSettingById(contactId).first()
            if (contact != null) {
                contactRepository.insertOrUpdateContactSetting(contact.copy(groupId = groupId))
            }
        }
    }
}
