package app.mykeys.crypto

/**
 * Small platform boundary around audited/native cryptographic implementations.
 * Protocol construction stays in common code; primitive implementations stay platform-specific.
 */
interface VaultCryptoPrimitives {
    fun randomBytes(size: Int): ByteArray

    fun sha256(input: ByteArray): ByteArray

    fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: Argon2idParameters,
    ): ByteArray

    fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputBytes: Int,
    ): ByteArray

    fun aes256GcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray

    fun aes256GcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray,
    ): ByteArray
}

expect fun createPlatformVaultCryptoPrimitives(): VaultCryptoPrimitives

