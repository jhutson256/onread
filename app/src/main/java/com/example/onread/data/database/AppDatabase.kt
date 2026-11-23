package com.example.onread.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.onread.data.dao.ContactDao
import com.example.onread.data.dao.GroupDao
import com.example.onread.data.dao.ReminderDao
import com.example.onread.data.entity.ContactSettingEntity
import com.example.onread.data.entity.GroupEntity
import com.example.onread.data.entity.ReminderEntity

@Database(entities = [ContactSettingEntity::class, ReminderEntity::class, GroupEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun reminderDao(): ReminderDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "text_reminder_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN sourceType TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN sourceMessageId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_sourceType_sourceMessageId " +
                        "ON reminders(sourceType, sourceMessageId)"
                )
            }
        }
    }
}
