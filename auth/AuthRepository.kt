package com.example.securechat.auth

import com.example.securechat.data.awaitTask
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signIn(
        email: String,
        password: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task -> onResult(task.toResult()) }
    }

    fun register(
        email: String,
        password: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task -> onResult(task.toResult()) }
    }

    /**
     * Sends Firebase's "reset your password" email.
     * Reports success even when no account uses that email, so the app never
     * reveals which emails are registered.
     */
    fun sendPasswordReset(
        email: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            val error = task.exception
            onResult(
                if (task.isSuccessful || error is FirebaseAuthInvalidUserException) {
                    Result.success(Unit)
                } else {
                    Result.failure(error ?: Exception("Could not send the email."))
                }
            )
        }
    }

    /**
     * Changes the password. Firebase requires a recent login for this,
     * so the current password is checked first.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: throw IllegalStateException("You're not logged in.")
        val email = user.email ?: throw IllegalStateException("This account has no email address.")

        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).awaitTask()
        user.updatePassword(newPassword).awaitTask()
    }

    fun signOut() {
        auth.signOut()
    }

    /**
     * Creates or updates the signed-in user's profile, including the public key
     * other users need to start an encrypted chat with them.
     * A display name set in Settings is kept (merge write).
     */
    fun saveProfile(
        user: FirebaseUser,
        publicKey: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val profile = hashMapOf(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "publicKey" to publicKey,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(user.uid)
            .set(profile, SetOptions.merge())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Sets or clears (empty text) the name other people see. */
    suspend fun updateDisplayName(uid: String, name: String) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        db.collection("users").document(uid).update(
            "displayName",
            if (clean.isEmpty()) FieldValue.delete() else clean
        ).awaitTask()
    }

    private fun Task<AuthResult>.toResult(): Result<Unit> =
        if (isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(exception ?: Exception("Authentication failed."))
        }

    companion object {
        const val MAX_NAME_LENGTH = 40

        /** Turns Firebase's technical error messages into short, clear ones. */
        fun friendlyMessage(error: Throwable?): String = when (error) {
            // Weak password is a kind of invalid-credentials error, so it is checked first
            is FirebaseAuthWeakPasswordException ->
                "That password is too weak. Use at least 8 characters."

            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Try logging in instead."

            is FirebaseAuthInvalidUserException ->
                "Wrong email or password."

            is FirebaseAuthInvalidCredentialsException ->
                if (error.errorCode == "ERROR_INVALID_EMAIL") {
                    "That email address doesn't look right."
                } else {
                    "Wrong email or password."
                }

            is FirebaseTooManyRequestsException ->
                "Too many attempts. Wait a few minutes and try again."

            is FirebaseNetworkException ->
                "No internet connection. Check your network and try again."

            else -> error?.localizedMessage ?: "Something went wrong. Please try again."
        }

        /** Same as [friendlyMessage], worded for the change-password dialog. */
        fun friendlyPasswordChangeMessage(error: Throwable?): String = when (error) {
            is FirebaseAuthWeakPasswordException ->
                "That new password is too weak. Use at least 8 characters."

            is FirebaseAuthInvalidCredentialsException ->
                "Your current password is wrong."

            else -> friendlyMessage(error)
        }
    }
}
