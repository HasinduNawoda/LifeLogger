package com.kdu.mc.lifelogger.data.repository

import com.kdu.mc.lifelogger.CalendarNote
import com.kdu.mc.lifelogger.UserProfile
import com.kdu.mc.lifelogger.data.local.CalendarNoteDao
import com.kdu.mc.lifelogger.data.local.UserProfileDao
import com.kdu.mc.lifelogger.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalendarNoteRepository(
    private val noteDao: CalendarNoteDao
) {
    fun observeNotes(uid: String): Flow<List<CalendarNote>> =
        noteDao.observeNotes(uid).map { list -> list.map { it.toCalendarNote() } }

    /** Add or update a note. Pass [existingLocalId] when editing an existing note. */
    suspend fun upsertNote(uid: String, note: CalendarNote, existingLocalId: Long? = null) {
        if (existingLocalId == null) {
            noteDao.insert(note.toEntity(ownerUid = uid))
        } else {
            val existing = noteDao.observeNotes(uid) // not ideal for lookups; prefer dao.findById in real impl
            noteDao.update(note.toEntity(ownerUid = uid).copy(localId = existingLocalId))
        }
    }

    suspend fun deleteNote(localId: Long) {
        noteDao.softDelete(localId)
    }
}

class ProfileRepository(
    private val profileDao: UserProfileDao
) {
    fun observeProfile(uid: String): Flow<UserProfile> =
        profileDao.observeProfile(uid).map { entity ->
            entity?.let {
                UserProfile(
                    name = it.name,
                    email = it.email,
                    bio = it.bio,
                    photoUri = it.localPhotoUri?.let { uri -> android.net.Uri.parse(uri) }
                )
            } ?: UserProfile()
        }

    suspend fun saveProfile(uid: String, profile: UserProfile) {
        val existing = profileDao.getProfile(uid)
        profileDao.upsert(
            UserProfileEntity(
                ownerUid = uid,
                name = profile.name,
                email = profile.email,
                bio = profile.bio,
                localPhotoUri = profile.photoUri?.toString(),
                remotePhotoUrl = existing?.remotePhotoUrl,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
        )
    }
}
