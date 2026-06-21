package com.kdu.mc.lifelogger.data

import android.content.Context
import com.kdu.mc.lifelogger.auth.AuthRepository
import com.kdu.mc.lifelogger.data.local.LifeLoggerDatabase
import com.kdu.mc.lifelogger.data.repository.CalendarNoteRepository
import com.kdu.mc.lifelogger.data.repository.EntryRepository
import com.kdu.mc.lifelogger.data.repository.ProfileRepository
import com.kdu.mc.lifelogger.sync.SyncManager

/**
 * Minimal manual dependency container. Construct once in MainActivity / Application
 * and pass down via parameters, or wrap in a CompositionLocal if preferred.
 *
 * Usage in MainActivity:
 *   val container = remember { AppContainer(applicationContext) }
 */
class AppContainer(context: Context) {

    private val db = LifeLoggerDatabase.getInstance(context)

    val authRepository = AuthRepository()

    val entryRepository = EntryRepository(db.entryDao())
    val calendarNoteRepository = CalendarNoteRepository(db.calendarNoteDao())
    val profileRepository = ProfileRepository(db.userProfileDao())

    val syncManager = SyncManager(
        context = context,
        entryDao = db.entryDao(),
        noteDao = db.calendarNoteDao(),
        profileDao = db.userProfileDao()
    )
}
