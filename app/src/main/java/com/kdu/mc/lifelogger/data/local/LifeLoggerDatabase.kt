package com.kdu.mc.lifelogger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database for LifeLogger.
 */
@Database(
    entities = [
        EntryEntity::class,
        EntryBlockEntity::class,
        CalendarNoteEntity::class,
        UserProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LifeLoggerDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao
    abstract fun calendarNoteDao(): CalendarNoteDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: LifeLoggerDatabase? = null

        fun getInstance(context: Context): LifeLoggerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeLoggerDatabase::class.java,
                    "lifelogger.db"
                )
                    // Safe for early development; replace with real migrations before release.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
