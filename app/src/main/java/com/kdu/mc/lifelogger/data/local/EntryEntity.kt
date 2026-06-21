package com.kdu.mc.lifelogger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: String? = null,          // Firestore doc id (null until first sync)
    val ownerUid: String,                  // Firebase Auth uid that owns this entry
    val title: String,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false         // soft delete -> trash
)
