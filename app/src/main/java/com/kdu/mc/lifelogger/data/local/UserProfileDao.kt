package com.kdu.mc.lifelogger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE ownerUid = :uid LIMIT 1")
    fun observeProfile(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE ownerUid = :uid LIMIT 1")
    suspend fun getProfile(uid: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET isSynced = 1, remotePhotoUrl = :remotePhotoUrl WHERE ownerUid = :uid")
    suspend fun markSynced(uid: String, remotePhotoUrl: String?)
}
