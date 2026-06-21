package com.kdu.mc.lifelogger.data.repository

import com.kdu.mc.lifelogger.LifeEntry
import com.kdu.mc.lifelogger.TrashedEntry
import com.kdu.mc.lifelogger.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for entries. The UI (HomeScreen, AddEntryScreen)
 * reads from Room via Flow; all writes go through here too. Sync to
 * Firebase happens separately via SyncRepository — this class never
 * touches the network directly so the app stays fully usable offline.
 */
class EntryRepository(
    private val entryDao: EntryDao
) {
    /** Active (non-deleted) entries for the home feed, newest first. */
    fun observeEntries(uid: String): Flow<List<LifeEntry>> =
        entryDao.observeEntries(uid).map { list -> list.map { it.toLifeEntry() } }

    /** Entries currently in trash. */
    fun observeTrashedEntries(uid: String): Flow<List<TrashedEntryWithLocalId>> =
        entryDao.observeTrashedEntries(uid).map { list ->
            list.map {
                val entity = it.entry
                TrashedEntryWithLocalId(entity.localId, it.toLifeEntry(), entity.updatedAt)
            }
        }

    /** Entries that have not been synced to the server yet. */
    fun observeUnsyncedEntries(uid: String): Flow<List<LifeEntry>> =
        entryDao.observeUnsyncedEntries(uid).map { list ->
            list.map { it.toLifeEntry() }
        }

    /** Insert a brand-new entry. Returns the new local row id. */
    suspend fun addEntry(uid: String, entry: LifeEntry): Long {
        val entryEntity = entry.toEntryEntity(ownerUid = uid)
        val localId = entryDao.insertEntry(entryEntity)
        entryDao.insertBlocks(entry.toBlockEntities(localId))
        return localId
    }

    /**
     * Update an existing entry identified by [localId]. Replaces all blocks
     * (simplest correct approach — block lists are small).
     */
    suspend fun updateEntry(uid: String, localId: Long, entry: LifeEntry) {
        val existing = entryDao.getEntryWithBlocks(localId)?.entry
        val updated = entry.toEntryEntity(ownerUid = uid, existing = existing).copy(localId = localId)
        entryDao.updateEntry(updated)
        entryDao.deleteBlocksForEntry(localId)
        entryDao.insertBlocks(entry.toBlockEntities(localId))
    }

    /** Moves entries to trash (soft delete). */
    suspend fun moveToTrash(localIds: List<Long>) {
        localIds.forEach { entryDao.softDelete(it) }
    }

    /** Restores entries from trash back to the home feed. */
    suspend fun restoreFromTrash(localIds: List<Long>) {
        localIds.forEach { entryDao.restore(it) }
    }

    /** Permanently removes entries (and cascades to their blocks). */
    suspend fun deletePermanently(localIds: List<Long>) {
        localIds.forEach { entryDao.hardDelete(it) }
    }

    /** Fetch a single entry with blocks (used when opening AddEntryScreen for editing). */
    suspend fun getEntry(localId: Long): EntryWithBlocks? = entryDao.getEntryWithBlocks(localId)
}

/** Wraps a LifeEntry with its Room row id so the UI can issue restore/delete actions. */
data class TrashedEntryWithLocalId(
    val localId: Long,
    val entry: LifeEntry,
    val deletedAt: Long
) {
    fun toTrashedEntry(): TrashedEntry = TrashedEntry(entry = entry, deletedAt = deletedAt)
}
