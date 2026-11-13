package com.example.onread.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_settings")
data class ContactSettingEntity(
    @PrimaryKey
    val contactId: String,
    val phoneNumber: String,
    val displayName: String,
    val isReminderEnabled: Boolean = true,
    val customDelayMinutes: Int? = null,
    val useSpecificTime: Boolean = false,
    val specificHour: Int = 9,
    val specificMinute: Int = 0,
    val groupId: String? = null,
    val customDelayUpdatedAt: Long = 0L,
    val groupAssignedAt: Long = 0L,
    val isSavedContact: Boolean = true
)
