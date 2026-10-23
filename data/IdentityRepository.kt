package com.example.securechat.data

import com.example.securechat.crypto.IdentityKeys
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException

/**
 * Each user's ECDH key pair (layer 2).
 *
 * Stored in users/{uid}/private/identity, which only that user can read
 * under the Firestore rules. Because it lives on the server, a new phone or a
 * reinstall gets the same keys back after logging in.
 */
class IdentityRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun cached(uid: String): IdentityKeys.Identity? =
        cache?.takeIf { it.first == uid }?.second

    fun clearCache() {
        cache = null
    }

    /** Loads this user's key pair, creating it the first time. */
    suspend fun ensureIdentity(user: FirebaseUser): IdentityKeys.Identity {
        cached(user.uid)?.let { return it }

        val ref = db.collection("users")
            .document(user.uid)
            .collection("private")
            .document("identity")

        var serverError: Exception? = null
        val fromServer = try {
            ref.get(Source.SERVER).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverError = e
            null
        }

        val identity = when {
            fromServer != null && fromServer.exists() -> fromServer.toIdentity()

            // The server confirmed there is no key pair yet: make one.
            fromServer != null -> createIdentity(ref)

            else -> {
                // Offline: use the copy Firestore keeps on this phone.
                // Never create a new key pair without asking the server first,
                // or an existing one could be replaced.
                val fromCache = try {
                    ref.get(Source.CACHE).awaitTask()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

                if (fromCache != null && fromCache.exists()) {
                    fromCache.toIdentity()
                } else {
                    throw serverError
                        ?: IllegalStateException("Connect to the internet to finish setting up encryption.")
                }
            }
        }

        cache = user.uid to identity
        return identity
    }

    private suspend fun createIdentity(ref: DocumentReference): IdentityKeys.Identity {
        val fresh = IdentityKeys.generate()

        ref.set(
            mapOf(
                "publicKey" to fresh.publicKeyBase64,
                "privateKey" to IdentityKeys.encodePrivateKey(fresh.privateKey),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).awaitTask()

        return fresh
    }

    private fun DocumentSnapshot.toIdentity(): IdentityKeys.Identity {
        val publicKey = getString("publicKey")
        val privateKey = getString("privateKey")

        if (publicKey == null || privateKey == null) {
            throw IllegalStateException("Your saved encryption key is incomplete.")
        }

        return IdentityKeys.Identity(
            publicKey = IdentityKeys.decodePublicKey(publicKey),
            privateKey = IdentityKeys.decodePrivateKey(privateKey),
            publicKeyBase64 = publicKey
        )
    }

    private companion object {
        // Kept for the life of the app process, so rotating the screen doesn't reload it.
        @Volatile
        var cache: Pair<String, IdentityKeys.Identity>? = null
    }
}
