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
    val customSpecificHour: Int? = null,
    val customSpecificMinute: Int? = null,
    val groupId: Long? = null
    val customDelayUpdatedAt: Long = 0L,
    val groupId: Long? = null,
    val groupAssignedAt: Long = 0L
)
