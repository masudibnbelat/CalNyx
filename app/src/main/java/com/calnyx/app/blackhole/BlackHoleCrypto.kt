// FILE: app/src/main/java/com/calnyx/app/blackhole/BlackHoleCrypto.kt
// REPLACE FILE - OPENFIX 3 (2026-11-06): encrypt/decrypt now report
// progress (bytes written) and copy with a 64KB buffer instead of
// the 8KB default, so big videos import / open noticeably faster.
// Old no-progress signatures kept, they delegate to the new ones.
// NEW FILE - add this file, do not replace BlackHoleManager.kt with it.
// AES/GCM encryption with a key stored in AndroidKeyStore.
// No extra library needed, no Rust needed, fully offline.
//
// How it works:
// - One AES-256 key, alias "CalNyxBlackHoleKey", lives only in AndroidKeyStore.
// - Every file gets its own random 12-byte IV.
// - Stored file = IV (12 bytes) + ciphertext + GCM tag (inside ciphertext).
// - File name stays the original name, so list / rename / delete / mime
//   logic in BlackHoleManager does not change.
// - Preview decrypts only in memory (photos) or to app cache (video /
//   audio / document), never to Pictures / Movies / gallery.
// - WARNING: uninstalling the app deletes the KeyStore key AND the
//   blackhole files. Encrypted files copied out of the app are useless
//   without that key. Restore files before uninstall.
// - Changing the blackhole secret in Settings does NOT re-encrypt files,
//   the KeyStore key is independent of the secret.
package com.calnyx.app.blackhole

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BlackHoleCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CalNyxBlackHoleKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    // OPENFIX 3: 64KB copy buffer (the old copyTo default was 8KB).
    // Bigger chunks = fewer cipher calls = faster big-file work.
    private const val COPY_BUFFER = 65536

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt [input] into [output].
     * Writes the IV first, then the ciphertext.
     * This function closes [output] (CipherOutputStream must be closed
     * to write the final GCM tag). It does NOT close [input].
     */
    fun encryptStream(input: InputStream, output: OutputStream) {
        encryptStream(input, output) {}
    }

    // OPENFIX 3: same encryption, but reports bytes written so the
    // Screen can show a real "Importing... 47%" progress bar.
    fun encryptStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv // GCM generates a 12-byte IV
        output.write(iv)
        val cipherOut = CipherOutputStream(output, cipher)
        try {
            var written = 0L
            val buf = ByteArray(COPY_BUFFER)
            while (true) {
                val r = input.read(buf)
                if (r == -1) break
                cipherOut.write(buf, 0, r)
                written += r
                onProgress(written)
            }
        } finally {
            cipherOut.close()
        }
    }

    /**
     * Decrypt [input] (IV + ciphertext) into [output].
     * Does NOT close either stream, the caller closes them with use {}.
     * Throws on a bad tag, which is how a plain (not yet migrated)
     * file is detected by the caller.
     */
    fun decryptStream(input: InputStream, output: OutputStream) {
        decryptStream(input, output) {}
    }

    // OPENFIX 3: same decryption, but reports bytes written so the
    // Screen can show a real "Opening... 47%" progress bar.
    fun decryptStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val iv = ByteArray(IV_SIZE)
        var read = 0
        while (read < IV_SIZE) {
            val r = input.read(iv, read, IV_SIZE - read)
            if (r == -1) throw IOException("Encrypted file too short")
            read += r
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        val cipherIn = CipherInputStream(input, cipher)
        var written = 0L
        val buf = ByteArray(COPY_BUFFER)
        while (true) {
            val r = cipherIn.read(buf)
            if (r == -1) break
            output.write(buf, 0, r)
            written += r
            onProgress(written)
        }
    }

    /** Decrypt a whole blackhole file into memory. Use only for photos. */
    fun decryptToBytes(file: File): ByteArray {
        FileInputStream(file).use { fis ->
            ByteArrayOutputStream().use { baos ->
                decryptStream(fis, baos)
                return baos.toByteArray()
            }
        }
    }
}
