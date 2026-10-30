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
        val USE_SPECIFIC_TIME = booleanPreferencesKey("use_specific_time")
        val SPECIFIC_TIME_HOUR = intPreferencesKey("specific_time_hour")
        val SPECIFIC_TIME_MINUTE = intPreferencesKey("specific_time_minute")
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

    val useSpecificTimeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_SPECIFIC_TIME] ?: false
        }

    val specificTimeHourFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SPECIFIC_TIME_HOUR] ?: 18 // Default 6:30 PM (18:30)
        }

    val specificTimeMinuteFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SPECIFIC_TIME_MINUTE] ?: 30
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

    suspend fun updateUseSpecificTime(useSpecific: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_SPECIFIC_TIME] = useSpecific
        }
    }

    suspend fun updateSpecificTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPECIFIC_TIME_HOUR] = hour
            preferences[PreferencesKeys.SPECIFIC_TIME_MINUTE] = minute
        }
    }
}
