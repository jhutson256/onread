package com.example.onread.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings_preferences")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val DEFAULT_DELAY_MINUTES = intPreferencesKey("default_delay_minutes")
        val SAVED_CONTACTS_ONLY = booleanPreferencesKey("saved_contacts_only")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val DEFAULT_CONTACT_REMINDER_ENABLED = booleanPreferencesKey("default_contact_reminder_enabled")
        val SNOOZE_ENABLED = booleanPreferencesKey("snooze_enabled")
        val SNOOZE_DURATION_MINUTES = intPreferencesKey("default_snooze_duration_minutes")
        val MAX_SNOOZE_COUNT = intPreferencesKey("max_snooze_count")
    }

    val colorThemeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.COLOR_THEME] ?: "SYSTEM"
        }

    val defaultDelayMinutesFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_DELAY_MINUTES] ?: 30
        }

    val savedContactsOnlyFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SAVED_CONTACTS_ONLY] ?: false
        }

    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_ONBOARDING] ?: false
        }

    val defaultContactReminderEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_CONTACT_REMINDER_ENABLED] ?: true
        }

    val snoozeEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SNOOZE_ENABLED] ?: true
        }

    val snoozeDurationMinutesFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SNOOZE_DURATION_MINUTES] ?: 15
        }

    val maxSnoozeCountFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MAX_SNOOZE_COUNT] ?: 3
        }

    suspend fun setOnboardingCompleted(enableReminders: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_ONBOARDING] = true
            preferences[PreferencesKeys.DEFAULT_CONTACT_REMINDER_ENABLED] = enableReminders
        }
    }

    suspend fun updateColorTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLOR_THEME] = theme
        }
    }

    suspend fun updateDefaultDelayMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_DELAY_MINUTES] = minutes
        }
    }

    suspend fun updateSavedContactsOnly(savedOnly: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVED_CONTACTS_ONLY] = savedOnly
        }
    }

    suspend fun updateDefaultContactReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_CONTACT_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun updateSnoozeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNOOZE_ENABLED] = enabled
        }
    }

    suspend fun updateSnoozeDurationMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNOOZE_DURATION_MINUTES] = minutes
        }
    }

    suspend fun updateMaxSnoozeCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_SNOOZE_COUNT] = count
        }
    }
}
