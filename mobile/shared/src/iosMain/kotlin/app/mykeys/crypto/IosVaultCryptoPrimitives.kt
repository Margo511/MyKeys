@file:OptIn(ExperimentalUnsignedTypes::class)

package app.mykeys.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

actual fun createPlatformVaultCryptoPrimitives(): VaultCryptoPrimitives = IosVaultCryptoPrimitives()

internal class IosVaultCryptoPrimitives : VaultCryptoPrimitives {
    private val provider = CryptographyProvider.Default

    init {
        if (!LibsodiumInitializer.isInitialized()) {
            var initialized = false
            LibsodiumInitializer.initializeWithCallback { initialized = true }
            check(initialized) { "libsodium initialization did not complete synchronously" }
        }
    }

    override fun randomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    override fun sha256(input: ByteArray): ByteArray =
        provider.get(SHA256).hasher().hashBlocking(input)

    override fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: Argon2idParameters,
    ): ByteArray {
        require(parameters.parallelism == 1) { "libsodium Argon2id requires parallelism 1" }
        require(password.all { it.toInt() in 0..127 }) { "Argon2id input must use the protocol ASCII encoding" }
        return PasswordHash.pwhash(
            outputLength = parameters.outputBytes,
            password = password.decodeToString(),
            salt = salt.asUByteArray(),
            opsLimit = parameters.iterations.toULong(),
            memLimit = parameters.memoryKiB * 1_024,
            algorithm = crypto_pwhash_argon2id_ALG_ARGON2ID13,
        ).asByteArray()
    }

    override fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputBytes: Int,
    ): ByteArray = provider.get(HKDF).secretDerivation(
        digest = SHA256,
        outputSize = outputBytes.bytes,
        salt = salt,
        info = info,
    ).deriveSecretToByteArrayBlocking(inputKeyMaterial)

    override fun aes256GcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = aesKey(key).cipher().encryptWithIvBlocking(nonce, plaintext, associatedData)

    override fun aes256GcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = aesKey(key).cipher().decryptWithIvBlocking(nonce, ciphertextAndTag, associatedData)

    private fun aesKey(key: ByteArray): AES.GCM.Key {
        require(key.size == VAULT_KEY_BYTES)
        return provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
    }
}

