// FILE: app/src/main/java/com/calnyx/app/vault/VaultCrypto.kt
// NEW FILE - add this file, do not replace VaultManager.kt with it.
// AES/GCM encryption with a key stored in AndroidKeyStore.
// No extra library needed, no Rust needed, fully offline.
//
// How it works:
// - One AES-256 key, alias "CalNyxVaultKey", lives only in AndroidKeyStore.
// - Every file gets its own random 12-byte IV.
// - Stored file = IV (12 bytes) + ciphertext + GCM tag (inside ciphertext).
// - File name stays the original name, so list / rename / delete / mime
//   logic in VaultManager does not change.
// - Preview decrypts only in memory (photos) or to app cache (video /
//   audio / document), never to Pictures / Movies / gallery.
// - WARNING: uninstalling the app deletes the KeyStore key AND the
//   vault files. Encrypted files copied out of the app are useless
//   without that key. Restore files before uninstall.
// - Changing the vault secret in Settings does NOT re-encrypt files,
//   the KeyStore key is independent of the secret.
package com.calnyx.app.vault

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

object VaultCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CalNyxVaultKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

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
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv // GCM generates a 12-byte IV
        output.write(iv)
        val cipherOut = CipherOutputStream(output, cipher)
        try {
            input.copyTo(cipherOut)
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
        cipherIn.copyTo(output)
    }

    /** Decrypt a whole vault file into memory. Use only for photos. */
    fun decryptToBytes(file: File): ByteArray {
        FileInputStream(file).use { fis ->
            ByteArrayOutputStream().use { baos ->
                decryptStream(fis, baos)
                return baos.toByteArray()
            }
        }
    }
}
