package com.kdu.mc.lifelogger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarNoteDao {

    @Query("SELECT * FROM calendar_notes WHERE ownerUid = :uid AND isDeleted = 0 ORDER BY dateKey ASC")
    fun observeNotes(uid: String): Flow<List<CalendarNoteEntity>>

    @Query("SELECT * FROM calendar_notes WHERE ownerUid = :uid AND dateKey = :dateKey AND isDeleted = 0")
    fun observeNotesForDate(uid: String, dateKey: String): Flow<List<CalendarNoteEntity>>

    @Insert
    suspend fun insert(note: CalendarNoteEntity): Long

    @Update
    suspend fun update(note: CalendarNoteEntity)

    @Query("UPDATE calendar_notes SET isDeleted = 1, updatedAt = :now, isSynced = 0 WHERE localId = :localId")
    suspend fun softDelete(localId: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM calendar_notes WHERE localId = :localId")
    suspend fun hardDelete(localId: Long)

    @Query("SELECT * FROM calendar_notes WHERE ownerUid = :uid AND isSynced = 0")
    suspend fun getUnsynced(uid: String): List<CalendarNoteEntity>

    @Query("UPDATE calendar_notes SET serverId = :serverId, isSynced = 1 WHERE localId = :localId")
    suspend fun markSynced(localId: Long, serverId: String)

    @Query("SELECT * FROM calendar_notes WHERE serverId = :serverId AND ownerUid = :uid LIMIT 1")
    suspend fun findByServerId(serverId: String, uid: String): CalendarNoteEntity?
}
