package com.example.onread.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_settings")
data class ContactSettingEntity(
    @PrimaryKey
    val contactId: String,
    val phoneNumber: String,
    val displayName: String,
    val isReminderEnabled: Boolean = false,
    val customDelayMinutes: Int? = null
)
