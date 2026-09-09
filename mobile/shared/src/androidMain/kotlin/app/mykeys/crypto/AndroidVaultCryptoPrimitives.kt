package app.mykeys.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters

actual fun createPlatformVaultCryptoPrimitives(): VaultCryptoPrimitives = AndroidVaultCryptoPrimitives()

internal class AndroidVaultCryptoPrimitives(
    private val secureRandom: SecureRandom = SecureRandom(),
) : VaultCryptoPrimitives {
    override fun randomBytes(size: Int): ByteArray {
        require(size > 0)
        return ByteArray(size).also(secureRandom::nextBytes)
    }

    override fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    override fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: Argon2idParameters,
    ): ByteArray {
        require(password.isNotEmpty())
        require(salt.size == KDF_SALT_BYTES)
        val configuration = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(parameters.memoryKiB)
            .withIterations(parameters.iterations)
            .withParallelism(parameters.parallelism)
            .build()
        val output = ByteArray(parameters.outputBytes)
        Argon2BytesGenerator().apply { init(configuration) }.generateBytes(password, output)
        return output
    }

    override fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputBytes: Int,
    ): ByteArray {
        require(inputKeyMaterial.isNotEmpty())
        require(salt.isNotEmpty())
        require(outputBytes > 0)
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(inputKeyMaterial, salt, info))
        return ByteArray(outputBytes).also { generator.generateBytes(it, 0, it.size) }
    }

    override fun aes256GcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = cipher(Cipher.ENCRYPT_MODE, key, nonce, plaintext, associatedData)

    override fun aes256GcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = try {
        cipher(Cipher.DECRYPT_MODE, key, nonce, ciphertextAndTag, associatedData)
    } catch (failure: AEADBadTagException) {
        throw SecurityException("AES-GCM authentication failed", failure)
    }

    private fun cipher(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        require(key.size == VAULT_KEY_BYTES)
        require(nonce.size == GCM_NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(input)
    }
}

