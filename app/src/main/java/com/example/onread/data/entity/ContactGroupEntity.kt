package com.example.onread.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_groups")
data class ContactGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isReminderEnabled: Boolean = true,
    val customDelayMinutes: Int = 30
)
