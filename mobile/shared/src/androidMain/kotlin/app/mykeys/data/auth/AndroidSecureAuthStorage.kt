package app.mykeys.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureAuthStorage(context: Context) : SecureAuthStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    override suspend fun read(key: String): String? = synchronized(lock) {
        val encoded = preferences.getString(key, null) ?: return@synchronized null
        try {
            val parts = encoded.split(':')
            if (parts.size != 3 || parts[0] != STORAGE_VERSION) {
                preferences.edit().remove(key).apply()
                return@synchronized null
            }
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(key.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            preferences.edit().remove(key).apply()
            null
        }
    }

    override suspend fun write(key: String, value: String) {
        synchronized(lock) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(key.toByteArray(StandardCharsets.UTF_8))
            val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val encoded = buildString {
                append(STORAGE_VERSION)
                append(':')
                append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                append(':')
                append(Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }
            check(preferences.edit().putString(key, encoded).commit())
        }
    }

    override suspend fun delete(key: String) {
        synchronized(lock) {
            preferences.edit().remove(key).commit()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "app.mykeys.auth.session.aes.v1"
        const val PREFERENCES_NAME = "mykeys_auth_ciphertext"
        const val STORAGE_VERSION = "v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
