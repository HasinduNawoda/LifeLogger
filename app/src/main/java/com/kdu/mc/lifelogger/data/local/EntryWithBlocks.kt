package com.kdu.mc.lifelogger.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class EntryWithBlocks(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "localId",
        entityColumn = "entryLocalId"
    )
    val blocks: List<EntryBlockEntity>
)
