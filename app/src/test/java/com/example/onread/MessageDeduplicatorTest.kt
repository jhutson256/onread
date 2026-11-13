package com.example.onread

import com.example.onread.util.MessageDeduplicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

class MessageDeduplicatorTest {

    @Before
    fun setUp() {
        MessageDeduplicator.clear()
    }

    @Test
    fun generateMessageFingerprint_bucketingTo30Seconds() {
        val baseTime = (1700000000000L / 30_000L) * 30_000L // aligned 30s bucket boundary
        val t1 = baseTime + 1000L
        val t2 = baseTime + 20000L
        val t3 = baseTime + 35000L // next 30s bucket

        val fp1 = MessageDeduplicator.generateMessageFingerprint("+15551234567", "Hello world", t1)
        val fp2 = MessageDeduplicator.generateMessageFingerprint("+15551234567", "Hello world", t2)
        val fp3 = MessageDeduplicator.generateMessageFingerprint("+15551234567", "Hello world", t3)

        assertEquals("Timestamps within the same 30s bucket must produce identical fingerprints", fp1, fp2)
        assertNotEquals("Timestamps in different 30s buckets must produce different fingerprints", fp1, fp3)
    }

    @Test
    fun generateMessageFingerprint_senderNormalization() {
        val t = 1700000010000L
        val fp1 = MessageDeduplicator.generateMessageFingerprint("+1 (555) 019-2831", "Test message", t)
        val fp2 = MessageDeduplicator.generateMessageFingerprint("555-019-2831", "Test message", t)
        val fp3 = MessageDeduplicator.generateMessageFingerprint("15550192831", "Test message", t)

        assertEquals("Phone numbers with different formatting must normalize to same fingerprint", fp1, fp2)
        assertEquals("Phone numbers with country code prefix must normalize to same fingerprint", fp1, fp3)

        val fp4 = MessageDeduplicator.generateMessageFingerprint("ServiceAlert", "Test message", t)
        val fp5 = MessageDeduplicator.generateMessageFingerprint("  servicealert  ", "Test message", t)
        assertEquals("Non-numeric sender must be normalized with trim and lowercase", fp4, fp5)
    }

    @Test
    fun generateMessageFingerprint_bodyNormalization() {
        val t = 1700000010000L
        val fp1 = MessageDeduplicator.generateMessageFingerprint("+15551234567", "  Hello World!  ", t)
        val fp2 = MessageDeduplicator.generateMessageFingerprint("+15551234567", "hello world!", t)

        assertEquals("Message body must be normalized with trim and lowercase", fp1, fp2)
    }

    @Test
    fun generateMessageFingerprint_sha256Format() {
        val fp = MessageDeduplicator.generateMessageFingerprint("+15551234567", "Hello", 10000L)
        assertEquals("SHA-256 hash must be 64 characters long", 64, fp.length)
        assertTrue("SHA-256 hash must contain only hex characters", fp.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun isDuplicate_blocksDuplicates() {
        val fp = "dummy_fingerprint_1"
        assertFalse("First call to isDuplicate must return false", MessageDeduplicator.isDuplicate(fp))
        assertTrue("Second call to isDuplicate must return true (duplicate blocked)", MessageDeduplicator.isDuplicate(fp))
    }

    @Test
    fun isDuplicateMessage_flow() {
        val sender = "+15551234567"
        val body = "Appointment Reminder"
        val timestamp = 1700000010000L

        assertFalse(
            "First ingestion of message must not be flagged as duplicate",
            MessageDeduplicator.isDuplicateMessage(sender, body, timestamp)
        )

        assertTrue(
            "Second ingestion of identical message within 30s bucket must be flagged as duplicate",
            MessageDeduplicator.isDuplicateMessage("5551234567", "  appointment reminder  ", timestamp + 5000L)
        )

        assertFalse(
            "Different body from same sender within 30s bucket must not be flagged as duplicate",
            MessageDeduplicator.isDuplicateMessage(sender, "Cancel Appointment", timestamp + 5000L)
        )

        assertFalse(
            "Identical message in next 30s time bucket must not be flagged as duplicate",
            MessageDeduplicator.isDuplicateMessage(sender, body, timestamp + 35000L)
        )
    }

    @Test
    fun lruCache_maxSize200Eviction() {
        for (i in 1..200) {
            val fp = "fingerprint_$i"
            assertFalse(MessageDeduplicator.isDuplicate(fp))
        }

        // Add 201st fingerprint
        assertFalse(MessageDeduplicator.isDuplicate("fingerprint_201"))

        // fingerprint_1 should be evicted because LRU capacity is 200
        assertFalse(
            "Evicted fingerprint_1 should return false as it is no longer in LRU cache",
            MessageDeduplicator.isDuplicate("fingerprint_1")
        )

        assertTrue(
            "Non-evicted fingerprint_200 should return true as it remains in cache",
            MessageDeduplicator.isDuplicate("fingerprint_200")
        )
    }

    @Test
    fun concurrentIsDuplicate_threadSafe() = runBlocking {
        val sender = "+15559998888"
        val body = "Concurrent message"
        val timestamp = 1700000000000L

        val results = (1..20).map {
            async(Dispatchers.Default) {
                MessageDeduplicator.isDuplicateMessage(sender, body, timestamp)
            }
        }.awaitAll()

        val allowedCount = results.count { !it }
        val blockedCount = results.count { it }

        assertEquals("Only 1 concurrent call should return false (allowed)", 1, allowedCount)
        assertEquals("19 concurrent calls should return true (blocked)", 19, blockedCount)
    }
}
