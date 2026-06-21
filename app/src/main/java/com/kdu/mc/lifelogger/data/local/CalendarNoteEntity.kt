package com.kdu.mc.lifelogger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_notes")
data class CalendarNoteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: String? = null,
    val ownerUid: String,
    val dateKey: String,
    val title: String,
    val description: String = "",
    val colorLabel: String,                // NoteColor enum name
    val reminderTimeMillis: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)
