package com.example.onread

import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.observer.SmsContentObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun savedContactsOnly_whenFalse_processesAllNumbers() {
        val savedContactsOnly = false

        val processUnsaved = SmsContentObserver.shouldProcessSms(
            savedContactsOnly = savedContactsOnly,
            isContactSaved = false
        )
        assertTrue("When savedContactsOnly is false, unsaved numbers should be processed", processUnsaved)

        val processSaved = SmsContentObserver.shouldProcessSms(
            savedContactsOnly = savedContactsOnly,
            isContactSaved = true
        )
        assertTrue("When savedContactsOnly is false, saved numbers should be processed", processSaved)
    }

    @Test
    fun savedContactsOnly_whenTrue_onlyProcessesSavedContacts() {
        val savedContactsOnly = true

        val processUnsaved = SmsContentObserver.shouldProcessSms(
            savedContactsOnly = savedContactsOnly,
            isContactSaved = false
        )
        assertFalse("When savedContactsOnly is true, unsaved numbers should NOT be processed", processUnsaved)

        val processSaved = SmsContentObserver.shouldProcessSms(
            savedContactsOnly = savedContactsOnly,
            isContactSaved = true
        )
        assertTrue("When savedContactsOnly is true, saved contacts should be processed", processSaved)
    }

    @Test
    fun mmsMessageClassification_identifiesIncomingAndOutgoing() {
        assertTrue("msg_box 1 (inbox) should be incoming", SmsContentObserver.isIncomingMms(1, 0))
        assertTrue("m_type 132 (retrieve conf) should be incoming", SmsContentObserver.isIncomingMms(0, 132))
        assertFalse("msg_box 2 should not be incoming", SmsContentObserver.isIncomingMms(2, 0))

        assertTrue("msg_box 2 (sent) should be outgoing", SmsContentObserver.isOutgoingMms(2, 0))
        assertTrue("m_type 128 (send req) should be outgoing", SmsContentObserver.isOutgoingMms(0, 128))
        assertFalse("msg_box 1 should not be outgoing", SmsContentObserver.isOutgoingMms(1, 0))
    }

    @Test
    fun normalizeMmsTimestamp_convertsSecondsToMilliseconds() {
        val secondsTimestamp = 1700000000L
        val expectedMs = 1700000000000L
        assertEquals(expectedMs, SmsContentObserver.normalizeMmsTimestamp(secondsTimestamp))

        val millisTimestamp = 1700000000000L
        assertEquals(millisTimestamp, SmsContentObserver.normalizeMmsTimestamp(millisTimestamp))

        val invalidTimestamp = 0L
        assertTrue(SmsContentObserver.normalizeMmsTimestamp(invalidTimestamp) > 0)
    }

    @Test
    fun normalizeSmsTimestamp_handlesMillisecondsAndSeconds() {
        val secondsTimestamp = 1700000000L
        val expectedMs = 1700000000000L
        assertEquals(expectedMs, SmsContentObserver.normalizeSmsTimestamp(secondsTimestamp))

        val millisTimestamp = 1700000000000L
        assertEquals(millisTimestamp, SmsContentObserver.normalizeSmsTimestamp(millisTimestamp))

        val invalidTimestamp = 0L
        assertTrue(SmsContentObserver.normalizeSmsTimestamp(invalidTimestamp) > 0)
    }

    @Test
    fun isLikelyPhoneNumber_identifiesPhoneNumbersAndShortcodes() {
        assertTrue(SmsContentObserver.isLikelyPhoneNumber("+15551234567"))
        assertTrue(SmsContentObserver.isLikelyPhoneNumber("5551234567"))
        assertTrue(SmsContentObserver.isLikelyPhoneNumber("12345"))
        assertTrue(SmsContentObserver.isLikelyPhoneNumber("user@example.com"))

        assertFalse(SmsContentObserver.isLikelyPhoneNumber("1"))
        assertFalse(SmsContentObserver.isLikelyPhoneNumber("12"))
        assertFalse(SmsContentObserver.isLikelyPhoneNumber(""))
        assertFalse(SmsContentObserver.isLikelyPhoneNumber(null))
    }

    @Test
    fun cleanMmsAddress_filtersInsertAddressTokenAndBlank() {
        assertNull(SmsContentObserver.cleanMmsAddress(null))
        assertNull(SmsContentObserver.cleanMmsAddress(""))
        assertNull(SmsContentObserver.cleanMmsAddress("   "))
        assertNull(SmsContentObserver.cleanMmsAddress("insert-address-token"))
        assertNull(SmsContentObserver.cleanMmsAddress("Insert-Address-Token"))

        assertEquals("+15551234567", SmsContentObserver.cleanMmsAddress(" +15551234567 "))
    }

    @Test
    fun determineMmsBody_extractsTextOrSubjectOrFallback() {
        assertEquals("Hello world", SmsContentObserver.determineMmsBody("Hello world", "Subject"))
        assertEquals("Photo subject", SmsContentObserver.determineMmsBody("", "Photo subject"))
        assertEquals("[Media / RCS message]", SmsContentObserver.determineMmsBody(null, null))
        assertEquals("[Media / RCS message]", SmsContentObserver.determineMmsBody("", "NoSubject"))
    }

    @Test
    fun isNewMessage_filtersHistoricalMessages() {
        val observerStartTime = 1000000L
        val initialMaxId = 50L

        assertFalse(
            "Message prior to observerStartTime should be rejected",
            SmsContentObserver.isNewMessage(
                timestamp = observerStartTime - 1000L,
                observerStartTime = observerStartTime,
                messageId = initialMaxId + 1L,
                initialMaxId = initialMaxId
            )
        )

        assertFalse(
            "Message with ID <= initialMaxId should be rejected",
            SmsContentObserver.isNewMessage(
                timestamp = observerStartTime + 1000L,
                observerStartTime = observerStartTime,
                messageId = initialMaxId,
                initialMaxId = initialMaxId
            )
        )

        assertTrue(
            "Message with timestamp >= observerStartTime AND messageId > initialMaxId should be accepted",
            SmsContentObserver.isNewMessage(
                timestamp = observerStartTime + 1000L,
                observerStartTime = observerStartTime,
                messageId = initialMaxId + 1L,
                initialMaxId = initialMaxId
            )
        )
    }

    @Test
    fun remindersSortedByTimestampDescending_placesNewestAtTop() {
        val r1 = ReminderEntity(
            id = 1,
            phoneNumber = "+111",
            contactName = "Alice",
            messageBody = "Old text",
            timestamp = 1000L,
            triggerTime = 2000L,
            status = "PENDING"
        )
        val r2 = ReminderEntity(
            id = 2,
            phoneNumber = "+222",
            contactName = "Bob",
            messageBody = "New text",
            timestamp = 5000L,
            triggerTime = 6000L,
            status = "PENDING"
        )
        val r3 = ReminderEntity(
            id = 3,
            phoneNumber = "+333",
            contactName = "Charlie",
            messageBody = "Mid text",
            timestamp = 3000L,
            triggerTime = 4000L,
            status = "PENDING"
        )

        val reminders = listOf(r1, r2, r3)
        val sorted = reminders.sortedByDescending { it.timestamp }

        assertEquals(2L, sorted[0].id)
        assertEquals(3L, sorted[1].id)
        assertEquals(1L, sorted[2].id)
    }
}
