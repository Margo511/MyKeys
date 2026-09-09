package app.mykeys.crypto

const val VAULT_CRYPTO_VERSION = 1
const val VAULT_KEY_BYTES = 32
const val GCM_NONCE_BYTES = 12
const val GCM_TAG_BYTES = 16
const val KDF_SALT_BYTES = 16
const val MAX_CIPHERTEXT_BYTES = 65_536

data class Argon2idParameters(
    val memoryKiB: Int = 65_536,
    val iterations: Int = 3,
    val parallelism: Int = 1,
    val outputBytes: Int = VAULT_KEY_BYTES,
) {
    init {
        require(memoryKiB in 8..1_048_576)
        require(iterations in 1..10)
        require(parallelism in 1..16)
        require(outputBytes in 16..64)
    }

    fun requireApprovedEnvelopeParameters() {
        require(memoryKiB == 65_536)
        require(iterations == 3)
        require(parallelism == 1)
        require(outputBytes == VAULT_KEY_BYTES)
    }
}

enum class KeyWrapperType(val wireName: String) {
    MasterPassword("master_password"),
    RecoveryKey("recovery_key"),
}

data class KdfMetadata(
    val memoryKiB: Int? = null,
    val iterations: Int? = null,
    val parallelism: Int? = null,
    val outputBytes: Int = VAULT_KEY_BYTES,
    val infoVersion: Int? = null,
) {
    companion object {
        fun argon2id(parameters: Argon2idParameters) = KdfMetadata(
            memoryKiB = parameters.memoryKiB,
            iterations = parameters.iterations,
            parallelism = parameters.parallelism,
            outputBytes = parameters.outputBytes,
        )

        fun recoveryV1() = KdfMetadata(outputBytes = VAULT_KEY_BYTES, infoVersion = 1)
    }

    fun requireArgon2id(): Argon2idParameters = Argon2idParameters(
        memoryKiB = requireNotNull(memoryKiB),
        iterations = requireNotNull(iterations),
        parallelism = requireNotNull(parallelism),
        outputBytes = outputBytes,
    )
}

data class VaultKeyEnvelope(
    val id: String,
    val vaultId: String,
    val wrapperType: KeyWrapperType,
    val wrappedDek: ByteArray,
    val nonce: ByteArray,
    val kdfSalt: ByteArray,
    val kdfAlgorithm: String,
    val kdfParameters: KdfMetadata,
    val cipherAlgorithm: String = "aes-256-gcm",
    val cryptoVersion: Int = VAULT_CRYPTO_VERSION,
) {
    init {
        require(wrappedDek.size == VAULT_KEY_BYTES + GCM_TAG_BYTES)
        require(nonce.size == GCM_NONCE_BYTES)
        require(kdfSalt.size == KDF_SALT_BYTES)
    }
}

data class EncryptedVaultItem(
    val vaultId: String,
    val itemId: String,
    val ciphertextAndTag: ByteArray,
    val nonce: ByteArray,
    val payloadSchemaVersion: Int,
    val cipherAlgorithm: String = "aes-256-gcm",
    val cryptoVersion: Int = VAULT_CRYPTO_VERSION,
) {
    init {
        require(ciphertextAndTag.size in (GCM_TAG_BYTES + 1)..MAX_CIPHERTEXT_BYTES)
        require(nonce.size == GCM_NONCE_BYTES)
        require(payloadSchemaVersion > 0)
    }
}

class UnlockedVaultKey internal constructor(bytes: ByteArray) {
    private val material = bytes.copyOf()
    private var destroyed = false

    init {
        require(bytes.size == VAULT_KEY_BYTES)
    }

    internal fun <T> use(block: (ByteArray) -> T): T {
        check(!destroyed) { "The vault key is no longer available" }
        return block(material)
    }

    fun destroy() {
        material.fill(0)
        destroyed = true
    }

    val isDestroyed: Boolean
        get() = destroyed
}

data class VaultSetupMaterial(
    val unlockedKey: UnlockedVaultKey,
    val masterPasswordEnvelope: VaultKeyEnvelope,
    val recoveryKeyEnvelope: VaultKeyEnvelope,
    val recoveryPhrase: String,
)

data class RotatedRecoveryMaterial(
    val envelope: VaultKeyEnvelope,
    val recoveryPhrase: String,
)

sealed class VaultCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidInput(message: String) : VaultCryptoException(message)

    class AuthenticationFailed(cause: Throwable? = null) :
        VaultCryptoException("The key or encrypted data could not be authenticated", cause)

    class UnsupportedFormat(message: String) : VaultCryptoException(message)
}

internal fun ByteArray.wipe() = fill(0)
