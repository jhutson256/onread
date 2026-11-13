package com.example.onread.util

import android.util.Log
import java.security.MessageDigest

class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun containsKey(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun clear() {
        map.clear()
    }
}

object MessageDeduplicator {

    private const val TAG = "MESSAGE_DEDUPLICATOR"
    private const val CACHE_CAPACITY = 200

    private val cache = LruCache<String, Long>(CACHE_CAPACITY)

    @Synchronized
    fun generateMessageFingerprint(sender: String, body: String, timestamp: Long): String {
        val roundedTime = (timestamp / 30_000L) * 30_000L
        val digits = sender.filter { it.isDigit() }
        val normalizedSender = when {
            digits.length >= 10 -> digits.takeLast(10)
            digits.isNotEmpty() -> digits
            else -> sender.trim().lowercase()
        }
        val normalizedBody = body.trim().lowercase()
        val rawInput = "$normalizedSender|$normalizedBody|$roundedTime"
        val digest = MessageDigest.getInstance("SHA-256").digest(rawInput.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun isDuplicate(fingerprint: String): Boolean {
        if (cache.containsKey(fingerprint)) {
            try {
                Log.d(TAG, "DUPLICATE BLOCKED: $fingerprint")
            } catch (_: Throwable) {
            }
            return true
        }
        cache.put(fingerprint, System.currentTimeMillis())
        return false
    }

    @Synchronized
    fun isDuplicateMessage(sender: String, body: String, timestamp: Long): Boolean {
        val fingerprint = generateMessageFingerprint(sender, body, timestamp)
        return isDuplicate(fingerprint)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
