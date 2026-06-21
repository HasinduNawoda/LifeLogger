package com.kdu.mc.lifelogger.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout:
 *
 * users/{uid}                         -> profile fields
 * users/{uid}/entries/{entryId}       -> one document per LifeEntry
 * users/{uid}/calendarNotes/{noteId}  -> one document per CalendarNote
 *
 * Keeping everything under users/{uid} makes per-user security rules simple
 * (a user can only read/write their own subtree).
 */
class FirestoreDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun entriesCol(uid: String) = userDoc(uid).collection("entries")
    private fun notesCol(uid: String) = userDoc(uid).collection("calendarNotes")

    // ── Entries ──────────────────────────────────────────────────────────────

    /** Creates a new entry doc (auto id) or overwrites an existing one if [existingDocId] is provided. */
    suspend fun upsertEntry(uid: String, existingDocId: String?, data: Map<String, Any?>): String {
        val ref = if (existingDocId != null) entriesCol(uid).document(existingDocId)
        else entriesCol(uid).document()

        ref.set(data, SetOptions.merge()).await()
        return ref.id
    }

    suspend fun deleteEntry(uid: String, docId: String) {
        entriesCol(uid).document(docId).delete().await()
    }

    suspend fun fetchAllEntries(uid: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = entriesCol(uid).get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    // ── Calendar notes ───────────────────────────────────────────────────────

    suspend fun upsertCalendarNote(uid: String, existingDocId: String?, data: Map<String, Any?>): String {
        val ref = if (existingDocId != null) notesCol(uid).document(existingDocId)
        else notesCol(uid).document()

        ref.set(data, SetOptions.merge()).await()
        return ref.id
    }

    suspend fun deleteCalendarNote(uid: String, docId: String) {
        notesCol(uid).document(docId).delete().await()
    }

    suspend fun fetchAllCalendarNotes(uid: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = notesCol(uid).get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    suspend fun upsertProfile(uid: String, data: Map<String, Any?>) {
        userDoc(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun fetchProfile(uid: String): Map<String, Any?>? {
        val snapshot = userDoc(uid).get().await()
        return if (snapshot.exists()) snapshot.data else null
    }
}
