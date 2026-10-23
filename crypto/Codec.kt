package com.example.securechat.crypto

import android.util.Base64

// android.util.Base64 works on every API level (java.util.Base64 needs API 26).

internal fun ByteArray.toBase64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)

internal fun String.fromBase64(): ByteArray =
    Base64.decode(this, Base64.NO_WRAP)
