package com.example.onread.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.onread.data.dao.ContactDao
import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.ReminderEntity

@Database(entities = [ContactSettingEntity::class, ReminderEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "text_reminder_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
