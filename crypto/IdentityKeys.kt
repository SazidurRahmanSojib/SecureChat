package com.example.securechat.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Layer 2: each user's P-256 key pair, and the ECDH key agreement that gives
 * both people in a chat the same AES key without ever sending it.
 */
object IdentityKeys {

    class Identity(
        val publicKey: PublicKey,
        val privateKey: PrivateKey,
        /** The public key exactly as stored in Firestore. */
        val publicKeyBase64: String
    )

    fun generate(): Identity {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()

        return Identity(pair.public, pair.private, pair.public.encoded.toBase64())
    }

    fun decodePublicKey(base64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(base64.fromBase64()))

    fun decodePrivateKey(base64: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(base64.fromBase64()))

    fun encodePrivateKey(key: PrivateKey): String = key.encoded.toBase64()

    /**
     * My private key + their public key → shared secret → HKDF, bound to the chat ID.
     * Both phones get the same 32-byte AES key.
     */
    fun layer2Key(myPrivateKey: PrivateKey, theirPublicKey: PublicKey, chatId: String): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(myPrivateKey)
        agreement.doPhase(theirPublicKey, true)
        val secret = agreement.generateSecret()

        val key = Hkdf.derive(
            secret = secret,
            salt = chatId.toByteArray(Charsets.UTF_8),
            info = "SecureChat layer2 v1".toByteArray(Charsets.UTF_8)
        )
        secret.fill(0)
        return key
    }
}
