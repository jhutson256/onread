package com.example.onread.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_groups")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isReminderEnabled: Boolean = true,
    val customDelayMinutes: Int? = null,
    val useSpecificTime: Boolean = false,
    val specificHour: Int = 9,
    val specificMinute: Int = 0
)
