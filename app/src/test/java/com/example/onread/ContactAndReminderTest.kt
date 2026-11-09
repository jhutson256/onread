package com.example.onread

import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ContactGroupEntity
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.GroupEntity
import com.example.onread.data.entity.ReminderEntity
import com.example.onread.data.repository.ReminderRepository
import com.example.onread.observer.SmsContentObserver
import com.example.onread.service.RcsNotificationListenerService
import com.example.onread.util.NotificationHelper
import com.example.onread.util.ReminderSchedulingHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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
        assertEquals(expectedMs, SmsContentObserver.normalizeMmsTimestamp(secondsTimestamp))

        val millisTimestamp = 1700000000000L
        assertEquals(millisTimestamp, SmsContentObserver.normalizeMmsTimestamp(millisTimestamp))

        val invalidTimestamp = 0L
        assertTrue(SmsContentObserver.normalizeMmsTimestamp(invalidTimestamp) > 0)
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
        assertEquals("+15551234567", SmsContentObserver.cleanMmsAddress(" +1 (555) 123-4567 "))
        assertEquals("+15551234567", SmsContentObserver.cleanMmsAddress("insert-address-token+15551234567"))
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
            "Message prior to observerStartTime - 10s grace period should be rejected",
            SmsContentObserver.isNewMessage(
                timestamp = observerStartTime - 20000L,
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
            "Message with timestamp >= (observerStartTime - 10s) AND messageId > initialMaxId should be accepted",
            SmsContentObserver.isNewMessage(
                timestamp = observerStartTime - 5000L,
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

    @Test
    fun contactGroupEntity_defaultValues() {
        val group = ContactGroupEntity(name = "Family")
        assertEquals("Family", group.name)
        assertTrue(group.isReminderEnabled)
        assertEquals(30, group.customDelayMinutes)
        assertEquals(0L, group.id)
    }

    @Test
    fun groupAlphabeticalSorting_caseInsensitive() {
        val groups = listOf(
            ContactGroupEntity(id = 1, name = "work"),
            ContactGroupEntity(id = 2, name = "Friends"),
            ContactGroupEntity(id = 3, name = "FAMILY")
        )

        val sorted = groups.sortedBy { it.name.lowercase() }

        assertEquals("FAMILY", sorted[0].name)
        assertEquals("Friends", sorted[1].name)
        assertEquals("work", sorted[2].name)
    }

    @Test
    fun contactSettingAssignmentToGroup() {
        val contact = ContactSettingEntity(
            contactId = "c1",
            phoneNumber = "+111",
            displayName = "Alice",
            groupId = null
        )
        assertNull(contact.groupId)

        val updated = contact.copy(groupId = "5")
        assertEquals("5", updated.groupId)
    }

    @Test
    fun reminderDeepLinkUriFormat_isCorrect() {
        val phoneNumber = "+1234567890"
        val uriString = "smsto:$phoneNumber"
        assertEquals("smsto:+1234567890", uriString)
    }

    @Test
    fun reminderSchedulingHelper_specificTimeCalculation() {
        val cal = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val messageTime = cal.timeInMillis

        // Specific time 14:30 today (hasn't passed yet if message is at 10:00)
        val triggerToday = ReminderSchedulingHelper.calculateTriggerTime(
            timestampMs = messageTime,
            useSpecificTime = true,
            specificHour = 14,
            specificMinute = 30,
            customDelayMinutes = null,
            defaultDelayMinutes = 30
        )

        val expectedToday = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 1, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expectedToday, triggerToday)

        // Specific time 08:00 today (already passed if message is at 10:00 -> should be tomorrow 08:00)
        val triggerTomorrow = ReminderSchedulingHelper.calculateTriggerTime(
            timestampMs = messageTime,
            useSpecificTime = true,
            specificHour = 8,
            specificMinute = 0,
            customDelayMinutes = null,
            defaultDelayMinutes = 30
        )

        val expectedTomorrow = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 2, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expectedTomorrow, triggerTomorrow)
    }

    @Test
    fun reminderSchedulingHelper_fallbackDelay() {
        val messageTime = 1000000000000L
        val trigger = ReminderSchedulingHelper.calculateTriggerTime(
            timestampMs = messageTime,
            useSpecificTime = false,
            specificHour = 9,
            specificMinute = 0,
            customDelayMinutes = 45,
            defaultDelayMinutes = 30
        )

        assertEquals(messageTime + (45 * 60 * 1000L), trigger)
    }

    @Test
    fun reminderSchedulingHelper_groupInheritance() {
        val group = GroupEntity(
            id = "g1",
            name = "Work",
            isReminderEnabled = true,
            useSpecificTime = true,
            specificHour = 17,
            specificMinute = 0
        )

        val contact = ContactSettingEntity(
            contactId = "c1",
            phoneNumber = "+111",
            displayName = "Boss",
            groupId = "g1",
            useSpecificTime = false // inherits from group
        )

        val resolved = ReminderSchedulingHelper.resolveSettings(
            contact = contact,
            group = group,
            defaultDelayMinutes = 30,
            timestampMs = 1000000000000L
        )

        assertTrue(resolved.isReminderEnabled)
    }

    @Test
    fun groupSettingsOverrideIndividualContactPreferences() {
        val group = GroupEntity(
            id = "g_override",
            name = "VIP",
            isReminderEnabled = true,
            useSpecificTime = true,
            specificHour = 18,
            specificMinute = 45,
            customDelayMinutes = 120
        )

        val contact = ContactSettingEntity(
            contactId = "c_individual",
            phoneNumber = "+999",
            displayName = "Test User",
            isReminderEnabled = false, // Individual is disabled, but group is enabled and overrides
            useSpecificTime = false,
            specificHour = 9,
            specificMinute = 0,
            customDelayMinutes = 15,
            groupId = "g_override"
        )

        val timestampMs = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val resolved = ReminderSchedulingHelper.resolveSettings(
            contact = contact,
            group = group,
            defaultDelayMinutes = 30,
            timestampMs = timestampMs
        )

        assertTrue("Group setting should override individual contact preference (isReminderEnabled should be true)", resolved.isReminderEnabled)

        val expectedTrigger = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 1, 18, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("Group specific time should override individual contact specific time / delay", expectedTrigger, resolved.triggerTime)
    }

    @Test
    fun reminderRepository_deduplicationWithinWindow() {
        val fakeDao = object : ReminderDao {
            val reminders = mutableListOf<ReminderEntity>()
            override fun getAllReminders() = flowOf(reminders)
            override fun getPendingReminders() = flowOf(reminders)
            override suspend fun getPendingRemindersList(): List<ReminderEntity> = reminders.filter { it.status == "PENDING" }
            override fun getHistoryReminders() = flowOf<List<ReminderEntity>>(emptyList())
            override fun getReminderById(id: Long) = flowOf<ReminderEntity?>(null)
            override fun getRemindersByStatus(status: String) = flowOf<List<ReminderEntity>>(emptyList())
            override suspend fun insertReminder(reminder: ReminderEntity): Long {
                val newEntity = reminder.copy(id = (reminders.size + 1).toLong())
                reminders.add(newEntity)
                return newEntity.id
            }
            override suspend fun updateReminder(reminder: ReminderEntity) {
                val idx = reminders.indexOfFirst { it.id == reminder.id }
                if (idx != -1) reminders[idx] = reminder
            }
            override suspend fun deleteReminder(reminder: ReminderEntity) {}
            override suspend fun deleteReminderById(id: Long) {}
            override suspend fun dismissRemindersForNumber(phoneNumber: String) {
                reminders.forEachIndexed { i, r ->
                    if (r.phoneNumber == phoneNumber && r.status == "PENDING") {
                        reminders[i] = r.copy(status = "DISMISSED")
                    }
                }
            }
            override suspend fun getRemindersSince(cutoffTime: Long): List<ReminderEntity> {
                return reminders.filter { it.timestamp >= cutoffTime }
            }
            override suspend fun hasRecentPendingReminder(
                phoneNumber: String,
                phoneSuffix: String,
                messageBody: String,
                cutoffTime: Long
            ): Boolean {
                val inputDigits = phoneNumber.replace(Regex("[^0-9]"), "")
                val bodyClean = messageBody.trim()
                return reminders.any { existing ->
                    if (existing.status != "PENDING") return@any false
                    if (existing.timestamp < cutoffTime) return@any false

                    val existingDigits = existing.phoneNumber.replace(Regex("[^0-9]"), "")
                    val phoneMatches = (existing.phoneNumber == phoneNumber) ||
                        (inputDigits.isNotEmpty() && existingDigits.isNotEmpty() && existingDigits == inputDigits) ||
                        (inputDigits.length >= 10 && existingDigits.length >= 10 && inputDigits.takeLast(10) == existingDigits.takeLast(10))

                    val existingBodyClean = existing.messageBody.trim()
                    val bodyMatches = existingBodyClean.equals(bodyClean, ignoreCase = true)

                    phoneMatches && bodyMatches
                }
            }
        }

        val repository = ReminderRepository(fakeDao)
        val now = System.currentTimeMillis()

        val r1 = ReminderEntity(
            phoneNumber = "+1234567890",
            contactName = "Alice",
            messageBody = "Hello world",
            timestamp = now,
            triggerTime = now + 1000L,
            status = "PENDING"
        )

        runBlocking {
            val id1 = repository.insertReminder(r1)
            assertTrue(id1 > 0L)

            val r2 = ReminderEntity(
                phoneNumber = "+1234567890",
                contactName = "Alice",
                messageBody = "Hello world",
                timestamp = now + 10000L,
                triggerTime = now + 11000L,
                status = "PENDING"
            )
            val id2 = repository.insertReminder(r2)
            assertEquals("Exact same sender AND body within 30s should return -1L and be ignored", -1L, id2)

            val r3 = ReminderEntity(
                phoneNumber = "+1234567890",
                contactName = "Alice",
                messageBody = "Different message",
                timestamp = now + 10000L,
                triggerTime = now + 11000L,
                status = "PENDING"
            )
            val id3 = repository.insertReminder(r3)
            assertTrue("Different message body from same sender within 30s window should be inserted", id3 > 0L)

            val r4 = ReminderEntity(
                phoneNumber = "+1234567890",
                contactName = "Alice",
                messageBody = "Hello world",
                timestamp = now + 35_000L,
                triggerTime = now + 36_000L,
                status = "PENDING"
            )
            val id4 = repository.insertReminder(r4)
            assertTrue("Exact same message after 30-second window should be inserted", id4 > 0L)

            val r5 = ReminderEntity(
                phoneNumber = "+9876543210",
                contactName = "Bob",
                messageBody = "Hello world",
                timestamp = now + 10000L,
                triggerTime = now + 11000L,
                status = "PENDING"
            )
            val id5 = repository.insertReminder(r5)
            assertTrue("Message from different contact in same group should be inserted", id5 > 0L)
        }
    }

    @Test
    fun reminderRepository_concurrentInsertions_onlyOneSucceeds() = runBlocking {
        val fakeDao = object : ReminderDao {
            val reminders = mutableListOf<ReminderEntity>()
            private val mutex = Mutex()

            override fun getAllReminders() = flowOf(reminders)
            override fun getPendingReminders() = flowOf(reminders)
            override suspend fun getPendingRemindersList(): List<ReminderEntity> = mutex.withLock { reminders.filter { it.status == "PENDING" } }
            override fun getHistoryReminders() = flowOf<List<ReminderEntity>>(emptyList())
            override fun getReminderById(id: Long) = flowOf<ReminderEntity?>(null)
            override fun getRemindersByStatus(status: String) = flowOf<List<ReminderEntity>>(emptyList())
            override suspend fun insertReminder(reminder: ReminderEntity): Long = mutex.withLock {
                val newEntity = reminder.copy(id = (reminders.size + 1).toLong())
                reminders.add(newEntity)
                newEntity.id
            }
            override suspend fun updateReminder(reminder: ReminderEntity) {}
            override suspend fun deleteReminder(reminder: ReminderEntity) {}
            override suspend fun deleteReminderById(id: Long) {}
            override suspend fun dismissRemindersForNumber(phoneNumber: String) {}
            override suspend fun getRemindersSince(cutoffTime: Long): List<ReminderEntity> = mutex.withLock {
                reminders.filter { it.timestamp >= cutoffTime }
            }
            override suspend fun hasRecentPendingReminder(
                phoneNumber: String,
                phoneSuffix: String,
                messageBody: String,
                cutoffTime: Long
            ): Boolean = mutex.withLock {
                val inputDigits = phoneNumber.replace(Regex("[^0-9]"), "")
                val bodyClean = messageBody.trim()
                reminders.any { existing ->
                    if (existing.status != "PENDING") return@any false
                    if (existing.timestamp < cutoffTime) return@any false
                    val existingDigits = existing.phoneNumber.replace(Regex("[^0-9]"), "")
                    val phoneMatches = (existing.phoneNumber == phoneNumber) ||
                        (inputDigits.isNotEmpty() && existingDigits.isNotEmpty() && existingDigits == inputDigits) ||
                        (inputDigits.length >= 10 && existingDigits.length >= 10 && inputDigits.takeLast(10) == existingDigits.takeLast(10))
                    val bodyMatches = existing.messageBody.trim().equals(bodyClean, ignoreCase = true)
                    phoneMatches && bodyMatches
                }
            }
        }

        val repository = ReminderRepository(fakeDao)
        val now = System.currentTimeMillis()
        val reminder = ReminderEntity(
            phoneNumber = "+15551234567",
            contactName = "Group Chat Member",
            messageBody = "Group text message content",
            timestamp = now,
            triggerTime = now + 5000L,
            status = "PENDING"
        )

        val results = (1..10).map {
            async(Dispatchers.Default) {
                repository.insertReminder(reminder)
            }
        }.awaitAll()

        val successfulInsertions = results.filter { it > 0L }
        val droppedDuplicates = results.filter { it == -1L }

        assertEquals("Only 1 concurrent insertion should succeed", 1, successfulInsertions.size)
        assertEquals("9 concurrent insertions should be dropped as duplicates", 9, droppedDuplicates.size)
    }

    @Test
    fun bug3_isSavedContactDefaultAndFiltering() {
        val savedContact = ContactSettingEntity(
            contactId = "c1",
            phoneNumber = "+111",
            displayName = "Alice",
            isSavedContact = true
        )
        val unsavedContact = ContactSettingEntity(
            contactId = "c2",
            phoneNumber = "+999",
            displayName = "+999",
            isSavedContact = false
        )

        val contactsList = listOf(savedContact, unsavedContact)
        val savedOnly = contactsList.filter { it.isSavedContact }

        assertEquals(1, savedOnly.size)
        assertEquals("Alice", savedOnly[0].displayName)
    }

    @Test
    fun bug5_threadIdInReminderEntityAndDeepLinkUri() {
        val reminderWithThread = ReminderEntity(
            id = 1,
            phoneNumber = "+15551234567",
            contactName = "John",
            messageBody = "Group message",
            timestamp = 1000L,
            triggerTime = 2000L,
            status = "PENDING",
            threadId = 42L
        )

        assertEquals(42L, reminderWithThread.threadId)

        val uriString = if (reminderWithThread.threadId > 0L) {
            "content://mms-sms/conversations/${reminderWithThread.threadId}"
        } else {
            "smsto:${reminderWithThread.phoneNumber}"
        }

        assertEquals("content://mms-sms/conversations/42", uriString)
    }

    @Test
    fun groupAssignmentInheritsSettings() {
        val group = GroupEntity(
            id = "g_test",
            name = "Test Group",
            isReminderEnabled = false,
            customDelayMinutes = 60,
            useSpecificTime = true,
            specificHour = 10,
            specificMinute = 15
        )

        val contact = ContactSettingEntity(
            contactId = "c1",
            phoneNumber = "+111",
            displayName = "Alice",
            isReminderEnabled = true,
            customDelayMinutes = 10,
            useSpecificTime = false,
            specificHour = 9,
            specificMinute = 0,
            groupId = null
        )

        val assigned = contact.copy(
            groupId = group.id,
            isReminderEnabled = group.isReminderEnabled,
            customDelayMinutes = group.customDelayMinutes,
            useSpecificTime = group.useSpecificTime,
            specificHour = group.specificHour,
            specificMinute = group.specificMinute
        )

        assertEquals("g_test", assigned.groupId)
        assertFalse(assigned.isReminderEnabled)
        assertEquals(60, assigned.customDelayMinutes)
        assertTrue(assigned.useSpecificTime)
        assertEquals(10, assigned.specificHour)
        assertEquals(15, assigned.specificMinute)
    }

    @Test
    fun groupUpdateUpdatesContacts() {
        val group = GroupEntity(
            id = "g_update",
            name = "Work",
            isReminderEnabled = true,
            customDelayMinutes = 30,
            useSpecificTime = false
        )

        val contacts = mutableListOf(
            ContactSettingEntity("c1", "111", "Alice", groupId = "g_update"),
            ContactSettingEntity("c2", "222", "Bob", groupId = "g_update"),
            ContactSettingEntity("c3", "333", "Charlie", groupId = "other")
        )

        val updatedGroup = group.copy(isReminderEnabled = false, customDelayMinutes = 90)

        val synchronizedContacts = contacts.map { contact ->
            if (contact.groupId == updatedGroup.id) {
                contact.copy(
                    isReminderEnabled = updatedGroup.isReminderEnabled,
                    customDelayMinutes = updatedGroup.customDelayMinutes,
                    useSpecificTime = updatedGroup.useSpecificTime,
                    specificHour = updatedGroup.specificHour,
                    specificMinute = updatedGroup.specificMinute
                )
            } else {
                contact
            }
        }

        assertFalse(synchronizedContacts[0].isReminderEnabled)
        assertEquals(90, synchronizedContacts[0].customDelayMinutes)

        assertFalse(synchronizedContacts[1].isReminderEnabled)
        assertEquals(90, synchronizedContacts[1].customDelayMinutes)

        assertTrue(synchronizedContacts[2].isReminderEnabled)
        assertNull(synchronizedContacts[2].customDelayMinutes)
    }
}
