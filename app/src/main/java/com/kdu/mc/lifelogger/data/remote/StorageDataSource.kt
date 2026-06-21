package com.kdu.mc.lifelogger.data.remote

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Storage layout:
 *
 * users/{uid}/entries/{entryServerId}/{blockId}.{ext}
 * users/{uid}/profile/avatar.jpg
 */
class StorageDataSource(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    /**
     * Uploads a local file (image/audio/video) and returns its public download URL.
     * [extension] should include the leading dot, e.g. ".jpg", ".mp4".
     */
    suspend fun uploadEntryMedia(
        uid: String,
        entryServerId: String,
        blockId: String,
        localUri: Uri,
        extension: String
    ): String {
        val ref = storage.reference
            .child("users/$uid/entries/$entryServerId/$blockId$extension")

        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadProfilePhoto(uid: String, localUri: Uri): String {
        val ref = storage.reference.child("users/$uid/profile/avatar.jpg")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun deleteEntryFolder(uid: String, entryServerId: String) {
        // Storage has no recursive delete; the sync engine deletes each known
        // block file individually using deleteBlobIfExists before removing
        // the Firestore document.
    }

    suspend fun deleteBlobIfExists(remoteUrl: String) {
        runCatching {
            storage.getReferenceFromUrl(remoteUrl).delete().await()
        }
    }
}
