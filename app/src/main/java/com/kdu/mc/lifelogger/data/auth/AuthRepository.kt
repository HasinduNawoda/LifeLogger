package com.kdu.mc.lifelogger.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around FirebaseAuth used by the login/register screens.
 * Throws the underlying FirebaseAuthException on failure so the UI layer
 * can map error codes to user-facing messages.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUid: String?
        get() = auth.currentUser?.uid

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    suspend fun register(email: String, password: String, displayName: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Registration failed: no user returned")

        if (displayName.isNotBlank()) {
            val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
            user.updateProfile(profileUpdate).await()
        }
        return user
    }

    suspend fun login(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return result.user ?: error("Login failed: no user returned")
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun logout() {
        auth.signOut()
    }

    /** Convenience for mapping common FirebaseAuth exceptions to friendly text. */
    fun friendlyError(e: Exception): String = when {
        e.message?.contains("badly formatted", ignoreCase = true) == true -> "Please enter a valid email address."
        e.message?.contains("password is invalid", ignoreCase = true) == true -> "Incorrect password."
        e.message?.contains("no user record", ignoreCase = true) == true -> "No account found with this email."
        e.message?.contains("email address is already in use", ignoreCase = true) == true -> "An account already exists with this email."
        e.message?.contains("WEAK_PASSWORD", ignoreCase = true) == true -> "Password must be at least 6 characters."
        e.message?.contains("network", ignoreCase = true) == true -> "Network error. Check your connection."
        else -> e.message ?: "Something went wrong. Please try again."
    }
}
