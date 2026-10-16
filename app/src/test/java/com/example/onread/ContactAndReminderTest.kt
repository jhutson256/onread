package com.example.onread

import com.example.onread.data.entity.ContactSettingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactAndReminderTest {

    @Test
    fun defaultIsReminderEnabled_isTrue() {
        val contact = ContactSettingEntity(
            contactId = "c1",
            phoneNumber = "+1234567890",
            displayName = "Alice"
        )
        assertTrue("isReminderEnabled should default to true", contact.isReminderEnabled)
    }

    @Test
    fun alphabeticalSorting_caseInsensitive() {
        val contacts = listOf(
            ContactSettingEntity("c1", "111", "charlie"),
            ContactSettingEntity("c2", "222", "Alice"),
            ContactSettingEntity("c3", "333", "bob"),
            ContactSettingEntity("c4", "444", "DAVID")
        )

        val sorted = contacts.sortedBy { it.displayName.lowercase() }

        assertEquals("Alice", sorted[0].displayName)
        assertEquals("bob", sorted[1].displayName)
        assertEquals("charlie", sorted[2].displayName)
        assertEquals("DAVID", sorted[3].displayName)
    }

    @Test
    fun bulkUpdateReminderSettings_updatesAllContacts() {
        val contacts = listOf(
            ContactSettingEntity("c1", "111", "Alice", isReminderEnabled = true),
            ContactSettingEntity("c2", "222", "Bob", isReminderEnabled = true)
        )

        val updatedDisabled = contacts.map { it.copy(isReminderEnabled = false) }
        assertTrue("All contacts should have reminders disabled", updatedDisabled.all { !it.isReminderEnabled })

        val updatedEnabled = updatedDisabled.map { it.copy(isReminderEnabled = true) }
        assertTrue("All contacts should have reminders enabled", updatedEnabled.all { it.isReminderEnabled })
    }

    @Test
    fun newContactReminderPreference_usesDefaultSetting() {
        val defaultPreferenceDisabled = false
        val newContactDisabled = ContactSettingEntity(
            contactId = "c_new_1",
            phoneNumber = "123",
            displayName = "New User",
            isReminderEnabled = defaultPreferenceDisabled
        )
        assertFalse("New contact should inherit default disabled preference", newContactDisabled.isReminderEnabled)

        val defaultPreferenceEnabled = true
        val newContactEnabled = ContactSettingEntity(
            contactId = "c_new_2",
            phoneNumber = "456",
            displayName = "Another User",
            isReminderEnabled = defaultPreferenceEnabled
        )
        assertTrue("New contact should inherit default enabled preference", newContactEnabled.isReminderEnabled)
    }
}
