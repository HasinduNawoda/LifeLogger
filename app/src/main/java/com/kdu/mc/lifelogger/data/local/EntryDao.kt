package com.kdu.mc.lifelogger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Transaction
    @Query("SELECT * FROM entries WHERE ownerUid = :uid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeEntries(uid: String): Flow<List<EntryWithBlocks>>

    @Transaction
    @Query("SELECT * FROM entries WHERE ownerUid = :uid AND isDeleted = 1 ORDER BY updatedAt DESC")
    fun observeTrashedEntries(uid: String): Flow<List<EntryWithBlocks>>

    @Transaction
    @Query("SELECT * FROM entries WHERE ownerUid = :uid AND isSynced = 0 AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeUnsyncedEntries(uid: String): Flow<List<EntryWithBlocks>>

    @Transaction
    @Query("SELECT * FROM entries WHERE localId = :localId")
    suspend fun getEntryWithBlocks(localId: Long): EntryWithBlocks?

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Insert
    suspend fun insertBlocks(blocks: List<EntryBlockEntity>)

    @Query("DELETE FROM entry_blocks WHERE entryLocalId = :entryLocalId")
    suspend fun deleteBlocksForEntry(entryLocalId: Long)

    @Query("UPDATE entries SET isDeleted = 1, updatedAt = :now, isSynced = 0 WHERE localId = :localId")
    suspend fun softDelete(localId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE entries SET isDeleted = 0, updatedAt = :now, isSynced = 0 WHERE localId = :localId")
    suspend fun restore(localId: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM entries WHERE localId = :localId")
    suspend fun hardDelete(localId: Long)

    // ── Sync helpers ─────────────────────────────────────────────────────────
    @Query("SELECT * FROM entries WHERE ownerUid = :uid AND isSynced = 0")
    suspend fun getUnsyncedEntries(uid: String): List<EntryEntity>

    @Query("SELECT * FROM entry_blocks WHERE entryLocalId = :entryLocalId")
    suspend fun getBlocks(entryLocalId: Long): List<EntryBlockEntity>

    @Query("SELECT * FROM entry_blocks WHERE entryLocalId = :entryLocalId AND isUploaded = 0 AND localUri IS NOT NULL")
    suspend fun getUnuploadedBlocks(entryLocalId: Long): List<EntryBlockEntity>

    @Update
    suspend fun updateBlock(block: EntryBlockEntity)

    @Query("UPDATE entries SET serverId = :serverId, isSynced = 1 WHERE localId = :localId")
    suspend fun markEntrySynced(localId: Long, serverId: String)

    @Query("SELECT * FROM entries WHERE serverId = :serverId AND ownerUid = :uid LIMIT 1")
    suspend fun findByServerId(serverId: String, uid: String): EntryEntity?
}
