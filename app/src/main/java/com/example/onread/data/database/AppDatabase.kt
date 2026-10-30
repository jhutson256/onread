package com.example.onread.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.onread.data.dao.ContactDao
import com.example.onread.data.dao.ContactGroupDao
import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ContactGroupEntity
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.ReminderEntity

@Database(entities = [ContactSettingEntity::class, ReminderEntity::class, ContactGroupEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun reminderDao(): ReminderDao
    abstract fun contactGroupDao(): ContactGroupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "text_reminder_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
