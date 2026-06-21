package com.kdu.mc.lifelogger.data.repository

import android.net.Uri
import com.kdu.mc.lifelogger.CalendarNote
import com.kdu.mc.lifelogger.EntryBlock
import com.kdu.mc.lifelogger.LifeEntry
import com.kdu.mc.lifelogger.NoteColor
import com.kdu.mc.lifelogger.data.local.CalendarNoteEntity
import com.kdu.mc.lifelogger.data.local.EntryBlockEntity
import com.kdu.mc.lifelogger.data.local.EntryEntity
import com.kdu.mc.lifelogger.data.local.EntryWithBlocks

// ── Block type <-> string ────────────────────────────────────────────────────

private fun EntryBlock.typeName(): String = when (this) {
    is EntryBlock.TextBlock -> "TEXT"
    is EntryBlock.ImageBlock -> "IMAGE"
    is EntryBlock.AudioBlock -> "AUDIO"
    is EntryBlock.VideoBlock -> "VIDEO"
}

private fun EntryBlock.uriOrNull(): Uri? = when (this) {
    is EntryBlock.ImageBlock -> uri
    is EntryBlock.AudioBlock -> uri
    is EntryBlock.VideoBlock -> uri
    is EntryBlock.TextBlock -> null
}

// ── EntryWithBlocks -> LifeEntry ─────────────────────────────────────────────

fun EntryWithBlocks.toLifeEntry(): LifeEntry {
    val orderedBlocks = blocks.sortedBy { it.position }.map { it.toEntryBlock() }
    return LifeEntry(
        title = entry.title,
        category = entry.category,
        blocks = orderedBlocks,
        createdAt = entry.createdAt,
        localId = entry.localId
    )
}

fun EntryBlockEntity.toEntryBlock(): EntryBlock = when (type) {
    "TEXT" -> EntryBlock.TextBlock(text.orEmpty())
    "IMAGE" -> EntryBlock.ImageBlock(Uri.parse(localUri ?: remoteUrl ?: ""))
    "AUDIO" -> EntryBlock.AudioBlock(Uri.parse(localUri ?: remoteUrl ?: ""))
    "VIDEO" -> EntryBlock.VideoBlock(Uri.parse(localUri ?: remoteUrl ?: ""))
    else -> EntryBlock.TextBlock("")
}

// ── LifeEntry -> Room entities ───────────────────────────────────────────────

/** Builds a new EntryEntity row (localId = 0, to be inserted). */
fun LifeEntry.toEntryEntity(ownerUid: String, existing: EntryEntity? = null): EntryEntity =
    existing?.copy(
        title = title,
        category = category,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        isSynced = false
    ) ?: EntryEntity(
        ownerUid = ownerUid,
        title = title,
        category = category,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        isSynced = false,
        isDeleted = false
    )

/** Builds the block rows for a given entry, preserving order via [position]. */
fun LifeEntry.toBlockEntities(entryLocalId: Long): List<EntryBlockEntity> =
    blocks.mapIndexed { index, block ->
        EntryBlockEntity(
            entryLocalId = entryLocalId,
            position = index,
            type = block.typeName(),
            text = (block as? EntryBlock.TextBlock)?.text,
            localUri = block.uriOrNull()?.toString(),
            remoteUrl = null,
            isUploaded = false
        )
    }

// ── CalendarNote <-> CalendarNoteEntity ──────────────────────────────────────

fun CalendarNoteEntity.toCalendarNote(): CalendarNote = CalendarNote(
    id = localId.toInt(),
    dateKey = dateKey,
    title = title,
    description = description,
    colorLabel = runCatching { NoteColor.valueOf(colorLabel) }.getOrDefault(NoteColor.BLUE),
    reminderTimeMillis = reminderTimeMillis
)

fun CalendarNote.toEntity(ownerUid: String, existing: CalendarNoteEntity? = null): CalendarNoteEntity =
    existing?.copy(
        dateKey = dateKey,
        title = title,
        description = description,
        colorLabel = colorLabel.name,
        reminderTimeMillis = reminderTimeMillis,
        updatedAt = System.currentTimeMillis(),
        isSynced = false
    ) ?: CalendarNoteEntity(
        ownerUid = ownerUid,
        dateKey = dateKey,
        title = title,
        description = description,
        colorLabel = colorLabel.name,
        reminderTimeMillis = reminderTimeMillis,
        updatedAt = System.currentTimeMillis(),
        isSynced = false,
        isDeleted = false
    )

// ── Firestore document maps ─────────────────────────────────────────────────

fun EntryWithBlocks.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to entry.title,
    "category" to entry.category,
    "createdAt" to entry.createdAt,
    "updatedAt" to entry.updatedAt,
    "blocks" to blocks.sortedBy { it.position }.map { block ->
        mapOf(
            "position" to block.position,
            "type" to block.type,
            "text" to block.text,
            "remoteUrl" to block.remoteUrl
        )
    }
)

fun CalendarNoteEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "dateKey" to dateKey,
    "title" to title,
    "description" to description,
    "colorLabel" to colorLabel,
    "reminderTimeMillis" to reminderTimeMillis,
    "updatedAt" to updatedAt
)
