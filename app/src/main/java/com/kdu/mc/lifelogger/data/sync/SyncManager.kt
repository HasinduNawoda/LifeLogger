package com.kdu.mc.lifelogger.sync

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.kdu.mc.lifelogger.data.local.CalendarNoteEntity
import com.kdu.mc.lifelogger.data.local.EntryDao
import com.kdu.mc.lifelogger.data.local.CalendarNoteDao
import com.kdu.mc.lifelogger.data.local.EntryEntity
import com.kdu.mc.lifelogger.data.local.UserProfileDao
import com.kdu.mc.lifelogger.data.remote.FirestoreDataSource
import com.kdu.mc.lifelogger.data.remote.StorageDataSource
import com.kdu.mc.lifelogger.data.repository.toFirestoreMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-way sync between Room (local) and Firebase (remote).
 */
class SyncManager(
    private val context: Context,
    private val entryDao: EntryDao,
    private val noteDao: CalendarNoteDao,
    private val profileDao: UserProfileDao,
    private val firestore: FirestoreDataSource = FirestoreDataSource(),
    private val storage: StorageDataSource = StorageDataSource()
) {

    sealed class SyncResult {
        data class Success(val entriesSynced: Int, val notesSynced: Int) : SyncResult()
        data class Failure(val message: String) : SyncResult()
    }

    /** Runs a full push of unsynced local data to Firebase. */
    suspend fun syncAll(uid: String, onEntrySynced: (Long) -> Unit = {}): SyncResult = withContext(Dispatchers.IO) {
        try {
            val entriesSynced = pushEntries(uid, onEntrySynced)
            val notesSynced = pushCalendarNotes(uid)
            pushProfile(uid)
            SyncResult.Success(entriesSynced, notesSynced)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Sync failed")
        }
    }

    // ── Entries ──────────────────────────────────────────────────────────────

    private suspend fun pushEntries(uid: String, onEntrySynced: (Long) -> Unit): Int {
        val unsynced = entryDao.getUnsyncedEntries(uid)
        var count = 0

        for (entryEntity in unsynced) {
            if (entryEntity.isDeleted) {
                entryEntity.serverId?.let { serverId ->
                    val blocks = entryDao.getBlocks(entryEntity.localId)
                    blocks.mapNotNull { it.remoteUrl }.forEach { storage.deleteBlobIfExists(it) }
                    firestore.deleteEntry(uid, serverId)
                }
                entryDao.hardDelete(entryEntity.localId)
                count++
                continue
            }

            val pendingBlocks = entryDao.getUnuploadedBlocks(entryEntity.localId)
            val provisionalServerId = entryEntity.serverId
                ?: firestore.upsertEntry(uid, null, mapOf("title" to entryEntity.title))

            for (block in pendingBlocks) {
                val localUri = block.localUri?.let { Uri.parse(it) } ?: continue
                val extension = guessExtension(localUri)
                val url = storage.uploadEntryMedia(
                    uid = uid,
                    entryServerId = provisionalServerId,
                    blockId = block.id.toString(),
                    localUri = localUri,
                    extension = extension
                )
                entryDao.updateBlock(block.copy(remoteUrl = url, isUploaded = true))
            }

            val withBlocks = entryDao.getEntryWithBlocks(entryEntity.localId) ?: continue
            val data = withBlocks.toFirestoreMap()
            val serverId = firestore.upsertEntry(uid, provisionalServerId, data)

            entryDao.markEntrySynced(entryEntity.localId, serverId)
            onEntrySynced(entryEntity.localId)
            count++
        }
        return count
    }

    // ── Calendar notes ───────────────────────────────────────────────────────

    private suspend fun pushCalendarNotes(uid: String): Int {
        val unsynced = noteDao.getUnsynced(uid)
        var count = 0

        for (note in unsynced) {
            if (note.isDeleted) {
                note.serverId?.let { firestore.deleteCalendarNote(uid, it) }
                noteDao.hardDelete(note.localId)
                count++
                continue
            }

            val serverId = firestore.upsertCalendarNote(uid, note.serverId, note.toFirestoreMap())
            noteDao.markSynced(note.localId, serverId)
            count++
        }
        return count
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    private suspend fun pushProfile(uid: String) {
        val profile = profileDao.getProfile(uid) ?: return
        if (profile.isSynced && profile.localPhotoUri == null) return

        var photoUrl = profile.remotePhotoUrl
        profile.localPhotoUri?.let { uriStr ->
            photoUrl = storage.uploadProfilePhoto(uid, Uri.parse(uriStr))
        }

        firestore.upsertProfile(
            uid, mapOf(
                "name" to profile.name,
                "email" to profile.email,
                "bio" to profile.bio,
                "photoUrl" to photoUrl,
                "updatedAt" to profile.updatedAt
            )
        )
        profileDao.markSynced(uid, photoUrl)
    }

    // ── Pull (remote -> local) ──────────────────────────────────────────────

    suspend fun pullEntries(uid: String) = withContext(Dispatchers.IO) {
        val remoteEntries = firestore.fetchAllEntries(uid)
        for ((docId, data) in remoteEntries) {
            val existing = entryDao.findByServerId(docId, uid)
            if (existing != null) continue

            val title = data["title"] as? String ?: continue
            val category = data["category"] as? String ?: "Other"
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt

            val localId = entryDao.insertEntry(
                EntryEntity(
                    serverId = docId,
                    ownerUid = uid,
                    title = title,
                    category = category,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    isSynced = true,
                    isDeleted = false
                )
            )

            @Suppress("UNCHECKED_CAST")
            val blocks = (data["blocks"] as? List<Map<String, Any?>>).orEmpty()
            val blockEntities = blocks.map { b ->
                com.kdu.mc.lifelogger.data.local.EntryBlockEntity(
                    entryLocalId = localId,
                    position = (b["position"] as? Number)?.toInt() ?: 0,
                    type = b["type"] as? String ?: "TEXT",
                    text = b["text"] as? String,
                    localUri = null,
                    remoteUrl = b["remoteUrl"] as? String,
                    isUploaded = true
                )
            }
            entryDao.insertBlocks(blockEntities)
        }
    }

    suspend fun pullCalendarNotes(uid: String) = withContext(Dispatchers.IO) {
        val remoteNotes = firestore.fetchAllCalendarNotes(uid)
        for ((docId, data) in remoteNotes) {
            val existing = noteDao.findByServerId(docId, uid)
            if (existing != null) continue

            noteDao.insert(
                CalendarNoteEntity(
                    serverId = docId,
                    ownerUid = uid,
                    dateKey = data["dateKey"] as? String ?: continue,
                    title = data["title"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    colorLabel = data["colorLabel"] as? String ?: "BLUE",
                    reminderTimeMillis = (data["reminderTimeMillis"] as? Number)?.toLong(),
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    isSynced = true,
                    isDeleted = false
                )
            )
        }
    }

    private fun guessExtension(uri: Uri): String {
        val type = context.contentResolver.getType(uri)
        val ext = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) ".$ext" else when {
            uri.toString().contains(".mp4") -> ".mp4"
            uri.toString().contains(".jpg") || uri.toString().contains(".jpeg") -> ".jpg"
            uri.toString().contains(".png") -> ".png"
            else -> ".bin"
        }
    }
}
