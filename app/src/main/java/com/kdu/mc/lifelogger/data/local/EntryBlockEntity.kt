package com.kdu.mc.lifelogger.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_blocks",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["localId"],
            childColumns = ["entryLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryLocalId: Long,
    val position: Int,                     // preserves block order
    val type: String,                      // "TEXT" | "IMAGE" | "AUDIO" | "VIDEO"
    val text: String? = null,              // used when type == TEXT
    val localUri: String? = null,          // file:// or content:// uri
    val remoteUrl: String? = null,         // Firebase Storage https url after upload
    val isUploaded: Boolean = false
)
