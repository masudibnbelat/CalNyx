// FILE: app/src/main/java/com/calnyx/app/BlueFilm/BlueFilmCrypto.kt
// NEW FILE - AES/GCM encryption for Blue Film recordings.
//
// Recordings are stored encrypted in the app's private storage,
// so a video file found on the phone cannot be played directly.
// A random 256-bit AES key is generated once and kept in the
// app's private SharedPreferences (no other app can read it).
// File format: [12-byte random IV][ciphertext + GCM tag].
// Encryption / decryption stream the file, so even long
// recordings never sit whole in memory.
package com.calnyx.app.BlueFilm

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BlueFilmCrypto {

    private const val PREFS = "bluefilm_crypto"
    private const val KEY_PREF = "aes_key_b64"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private fun getOrCreateKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PREF, null)
        if (saved != null) {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            return SecretKeySpec(bytes, "AES")
        }
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        prefs.edit()
            .putString(KEY_PREF, Base64.encodeToString(key.encoded, Base64.NO_WRAP))
            .apply()
        return key
    }

    fun encryptFile(context: Context, input: File, output: File) {
        val key = getOrCreateKey(context)
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        FileInputStream(input).use { fis ->
            FileOutputStream(output).use { fos ->
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    fis.copyTo(cos)
                }
            }
        }
    }

    fun decryptFile(context: Context, input: File, output: File) {
        val key = getOrCreateKey(context)
        FileInputStream(input).use { fis ->
            val iv = ByteArray(IV_BYTES)
            var read = 0
            while (read < IV_BYTES) {
                val n = fis.read(iv, read, IV_BYTES - read)
                if (n < 0) throw IllegalStateException("Blue Film file is too short")
                read += n
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            FileOutputStream(output).use { fos ->
                CipherInputStream(fis, cipher).use { cis ->
                    cis.copyTo(fos)
                }
            }
        }
    }
}
