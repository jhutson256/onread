package com.example.onread.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_groups")
data class ContactGroupEntity(
    @PrimaryKey
    val groupId: String,
    val name: String,
    val isReminderEnabled: Boolean = true,
    val delayMinutes: Int = 30
)
