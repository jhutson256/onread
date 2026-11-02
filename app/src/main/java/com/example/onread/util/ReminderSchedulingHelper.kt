package com.example.onread.util

import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.GroupEntity
import java.util.Calendar

object ReminderSchedulingHelper {

    data class ResolvedSettings(
        val isReminderEnabled: Boolean,
        val triggerTime: Long
    )

    fun calculateTriggerTime(
        timestampMs: Long,
        useSpecificTime: Boolean,
        specificHour: Int,
        specificMinute: Int,
        customDelayMinutes: Int?,
        defaultDelayMinutes: Int
    ): Long {
        if (useSpecificTime) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestampMs
                set(Calendar.HOUR_OF_DAY, specificHour)
                set(Calendar.MINUTE, specificMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= timestampMs) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        } else {
            val delay = customDelayMinutes ?: defaultDelayMinutes
            return timestampMs + (delay * 60 * 1000L)
        }
    }

    fun resolveSettings(
        contact: ContactSettingEntity,
        group: GroupEntity?,
        defaultDelayMinutes: Int,
        timestampMs: Long
    ): ResolvedSettings {
        // Inheritance: if contact is assigned to a group and doesn't override specific time/delay or reminder enabled, inherit from group.
        // Let's implement robust inheritance rules:
        // - isReminderEnabled: if contact has default or inherits, or group is disabled. If group is disabled, reminder is disabled (unless contact explicitly enables).
        val isEnabled = if (group != null && contact.groupId == group.id) {
            contact.isReminderEnabled && group.isReminderEnabled
        } else {
            contact.isReminderEnabled
        }

        // useSpecificTime: if contact overrides or group applies
        val useSpecific = if (group != null && contact.groupId == group.id) {
            contact.useSpecificTime || group.useSpecificTime
        } else {
            contact.useSpecificTime
        }

        val specificHour = if (group != null && contact.groupId == group.id && !contact.useSpecificTime && group.useSpecificTime) {
            group.specificHour
        } else {
            contact.specificHour
        }

        val specificMinute = if (group != null && contact.groupId == group.id && !contact.useSpecificTime && group.useSpecificTime) {
            group.specificMinute
        } else {
            contact.specificMinute
        }

        val delay = if (group != null && contact.groupId == group.id && contact.customDelayMinutes == null) {
            group.customDelayMinutes
        } else {
            contact.customDelayMinutes
        }

        val triggerTime = calculateTriggerTime(
            timestampMs = timestampMs,
            useSpecificTime = useSpecific,
            specificHour = specificHour,
            specificMinute = specificMinute,
            customDelayMinutes = delay,
            defaultDelayMinutes = defaultDelayMinutes
        )

        return ResolvedSettings(
            isReminderEnabled = isEnabled,
            triggerTime = triggerTime
        )
    }
}
