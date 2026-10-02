package com.example.onread.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val phoneNumber: String,
    val contactName: String,
    val messageBody: String,
    val timestamp: Long,
    val triggerTime: Long,
    val status: String // e.g., "PENDING", "SENT", "CANCELLED", "FAILED"
)
