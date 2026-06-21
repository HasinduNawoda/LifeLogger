package com.kdu.mc.lifelogger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val ownerUid: String,
    val name: String = "",
    val email: String = "",
    val bio: String = "",
    val localPhotoUri: String? = null,
    val remotePhotoUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
