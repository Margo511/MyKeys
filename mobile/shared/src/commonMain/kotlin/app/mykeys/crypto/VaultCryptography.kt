package app.mykeys.crypto

class VaultCryptography(
    private val primitives: VaultCryptoPrimitives,
    private val argon2idParameters: Argon2idParameters = Argon2idParameters(),
) {
    private val recoveryKeys = RecoveryKeyCodec(primitives)

    init {
        argon2idParameters.requireApprovedEnvelopeParameters()
    }

    fun createVaultKeyMaterial(
        vaultId: String,
        masterEnvelopeId: String,
        recoveryEnvelopeId: String,
        masterPassword: String,
    ): VaultSetupMaterial {
        validateIdentifier(vaultId, "vault")
        validateIdentifier(masterEnvelopeId, "envelope")
        validateIdentifier(recoveryEnvelopeId, "envelope")
        val dek = checkedRandom(VAULT_KEY_BYTES)
        return try {
            val recoverySeed = checkedRandom(VAULT_KEY_BYTES)
            try {
                val unlockedKey = UnlockedVaultKey(dek)
                try {
                    VaultSetupMaterial(
                        unlockedKey = unlockedKey,
                        masterPasswordEnvelope = wrapWithMasterPassword(
                            vaultId,
                            masterEnvelopeId,
                            dek,
                            masterPassword,
                        ),
                        recoveryKeyEnvelope = wrapWithRecoverySeed(
                            vaultId,
                            recoveryEnvelopeId,
                            dek,
                            recoverySeed,
                        ),
                        recoveryPhrase = recoveryKeys.encode(recoverySeed),
                    )
                } catch (failure: Throwable) {
                    unlockedKey.destroy()
                    throw failure
                }
            } finally {
                recoverySeed.wipe()
            }
        } finally {
            dek.wipe()
        }
    }

    fun unlockWithMasterPassword(
        envelope: VaultKeyEnvelope,
        masterPassword: String,
    ): UnlockedVaultKey {
        requireEnvelope(envelope, KeyWrapperType.MasterPassword, "argon2id-v1")
        val parameters = try {
            envelope.kdfParameters.requireArgon2id().also {
                it.requireApprovedEnvelopeParameters()
            }
        } catch (failure: IllegalArgumentException) {
            throw VaultCryptoException.UnsupportedFormat("The Argon2id parameters are not supported")
        }
        val encodedPassword = encodeMasterPassword(masterPassword)
        return try {
            val kek = primitives.argon2id(encodedPassword, envelope.kdfSalt, parameters)
            unwrap(envelope, kek)
        } finally {
            encodedPassword.wipe()
        }
    }

    fun unlockWithRecoveryKey(
        envelope: VaultKeyEnvelope,
        recoveryPhrase: String,
    ): UnlockedVaultKey {
        requireEnvelope(envelope, KeyWrapperType.RecoveryKey, "hkdf-sha256-v1")
        val seed = recoveryKeys.decode(recoveryPhrase)
        return try {
            val kek = deriveRecoveryKek(seed, envelope.kdfSalt, envelope.vaultId, envelope.id)
            unwrap(envelope, kek)
        } finally {
            seed.wipe()
        }
    }

    fun rotateMasterPassword(
        currentEnvelope: VaultKeyEnvelope,
        currentMasterPassword: String,
        newEnvelopeId: String,
        newMasterPassword: String,
    ): VaultKeyEnvelope {
        validateIdentifier(newEnvelopeId, "envelope")
        val key = unlockWithMasterPassword(currentEnvelope, currentMasterPassword)
        return try {
            key.use { dek ->
                wrapWithMasterPassword(currentEnvelope.vaultId, newEnvelopeId, dek, newMasterPassword)
            }
        } finally {
            key.destroy()
        }
    }

    fun rotateRecoveryKey(
        currentEnvelope: VaultKeyEnvelope,
        currentRecoveryPhrase: String,
        newEnvelopeId: String,
    ): RotatedRecoveryMaterial {
        validateIdentifier(newEnvelopeId, "envelope")
        val key = unlockWithRecoveryKey(currentEnvelope, currentRecoveryPhrase)
        val seed = checkedRandom(VAULT_KEY_BYTES)
        return try {
            val phrase = recoveryKeys.encode(seed)
            RotatedRecoveryMaterial(
                envelope = key.use { dek ->
                    wrapWithRecoverySeed(currentEnvelope.vaultId, newEnvelopeId, dek, seed)
                },
                recoveryPhrase = phrase,
            )
        } finally {
            seed.wipe()
            key.destroy()
        }
    }

    fun encryptItem(
        key: UnlockedVaultKey,
        vaultId: String,
        itemId: String,
        payloadSchemaVersion: Int,
        plaintext: ByteArray,
    ): EncryptedVaultItem {
        validateIdentifier(vaultId, "vault")
        validateIdentifier(itemId, "item")
        if (payloadSchemaVersion <= 0) {
            throw VaultCryptoException.InvalidInput("The payload schema version must be positive")
        }
        if (plaintext.isEmpty() || plaintext.size + GCM_TAG_BYTES > MAX_CIPHERTEXT_BYTES) {
            throw VaultCryptoException.InvalidInput("The encrypted payload must fit within 64 KiB")
        }
        val nonce = checkedRandom(GCM_NONCE_BYTES)
        val encrypted = key.use { dek ->
            primitives.aes256GcmEncrypt(
                dek,
                nonce,
                plaintext,
                itemAad(vaultId, itemId, payloadSchemaVersion),
            )
        }
        return EncryptedVaultItem(vaultId, itemId, encrypted, nonce, payloadSchemaVersion)
    }

    fun decryptItem(key: UnlockedVaultKey, item: EncryptedVaultItem): ByteArray {
        validateIdentifier(item.vaultId, "vault")
        validateIdentifier(item.itemId, "item")
        requireSupported(item.cryptoVersion, item.cipherAlgorithm)
        return decryptAuthenticated {
            key.use { dek ->
                primitives.aes256GcmDecrypt(
                    dek,
                    item.nonce,
                    item.ciphertextAndTag,
                    itemAad(item.vaultId, item.itemId, item.payloadSchemaVersion),
                )
            }
        }
    }

    private fun wrapWithMasterPassword(
        vaultId: String,
        envelopeId: String,
        dek: ByteArray,
        masterPassword: String,
    ): VaultKeyEnvelope {
        validateIdentifier(envelopeId, "envelope")
        val salt = checkedRandom(KDF_SALT_BYTES)
        val nonce = checkedRandom(GCM_NONCE_BYTES)
        val encodedPassword = encodeMasterPassword(masterPassword)
        return try {
            val kek = primitives.argon2id(encodedPassword, salt, argon2idParameters)
            try {
                VaultKeyEnvelope(
                    id = envelopeId,
                    vaultId = vaultId,
                    wrapperType = KeyWrapperType.MasterPassword,
                    wrappedDek = primitives.aes256GcmEncrypt(
                        kek,
                        nonce,
                        dek,
                        envelopeAad(vaultId, envelopeId, KeyWrapperType.MasterPassword, "argon2id-v1"),
                    ),
                    nonce = nonce,
                    kdfSalt = salt,
                    kdfAlgorithm = "argon2id-v1",
                    kdfParameters = KdfMetadata.argon2id(argon2idParameters),
                )
            } finally {
                kek.wipe()
            }
        } finally {
            encodedPassword.wipe()
        }
    }

    private fun wrapWithRecoverySeed(
        vaultId: String,
        envelopeId: String,
        dek: ByteArray,
        recoverySeed: ByteArray,
    ): VaultKeyEnvelope {
        validateIdentifier(envelopeId, "envelope")
        val salt = checkedRandom(KDF_SALT_BYTES)
        val nonce = checkedRandom(GCM_NONCE_BYTES)
        val kek = deriveRecoveryKek(recoverySeed, salt, vaultId, envelopeId)
        return try {
            VaultKeyEnvelope(
                id = envelopeId,
                vaultId = vaultId,
                wrapperType = KeyWrapperType.RecoveryKey,
                wrappedDek = primitives.aes256GcmEncrypt(
                    kek,
                    nonce,
                    dek,
                    envelopeAad(vaultId, envelopeId, KeyWrapperType.RecoveryKey, "hkdf-sha256-v1"),
                ),
                nonce = nonce,
                kdfSalt = salt,
                kdfAlgorithm = "hkdf-sha256-v1",
                kdfParameters = KdfMetadata.recoveryV1(),
            )
        } finally {
            kek.wipe()
        }
    }

    private fun deriveRecoveryKek(
        seed: ByteArray,
        salt: ByteArray,
        vaultId: String,
        envelopeId: String,
    ): ByteArray = primitives.hkdfSha256(
        inputKeyMaterial = seed,
        salt = salt,
        info = "mykeys:recovery-kek:v1|$vaultId|$envelopeId".encodeToByteArray(),
        outputBytes = VAULT_KEY_BYTES,
    )

    private fun unwrap(envelope: VaultKeyEnvelope, kek: ByteArray): UnlockedVaultKey = try {
        val dek = decryptAuthenticated {
            primitives.aes256GcmDecrypt(
                kek,
                envelope.nonce,
                envelope.wrappedDek,
                envelopeAad(
                    envelope.vaultId,
                    envelope.id,
                    envelope.wrapperType,
                    envelope.kdfAlgorithm,
                ),
            )
        }
        try {
            if (dek.size != VAULT_KEY_BYTES) {
                throw VaultCryptoException.AuthenticationFailed()
            }
            UnlockedVaultKey(dek)
        } finally {
            dek.wipe()
        }
    } finally {
        kek.wipe()
    }

    private fun requireEnvelope(
        envelope: VaultKeyEnvelope,
        wrapperType: KeyWrapperType,
        kdfAlgorithm: String,
    ) {
        validateIdentifier(envelope.vaultId, "vault")
        validateIdentifier(envelope.id, "envelope")
        requireSupported(envelope.cryptoVersion, envelope.cipherAlgorithm)
        if (envelope.wrapperType != wrapperType || envelope.kdfAlgorithm != kdfAlgorithm) {
            throw VaultCryptoException.UnsupportedFormat("The key envelope type is not supported for this operation")
        }
        if (
            wrapperType == KeyWrapperType.RecoveryKey &&
            (envelope.kdfParameters.infoVersion != 1 || envelope.kdfParameters.outputBytes != VAULT_KEY_BYTES)
        ) {
            throw VaultCryptoException.UnsupportedFormat("The recovery-key parameters are not supported")
        }
    }

    private fun requireSupported(cryptoVersion: Int, cipherAlgorithm: String) {
        if (cryptoVersion != VAULT_CRYPTO_VERSION || cipherAlgorithm != "aes-256-gcm") {
            throw VaultCryptoException.UnsupportedFormat("The encrypted data uses an unsupported crypto format")
        }
    }

    private fun encodeMasterPassword(password: String): ByteArray {
        val utf8 = password.encodeToByteArray()
        if (password.length < 12 || utf8.size > 1_024) {
            utf8.wipe()
            throw VaultCryptoException.InvalidInput("The master password must contain at least 12 characters and at most 1,024 UTF-8 bytes")
        }
        return try {
            utf8.toHexAscii()
        } finally {
            utf8.wipe()
        }
    }

    private fun ByteArray.toHexAscii(): ByteArray {
        val alphabet = "0123456789abcdef"
        return ByteArray(size * 2) { outputIndex ->
            val value = this[outputIndex / 2].toInt() and 0xff
            alphabet[if (outputIndex % 2 == 0) value ushr 4 else value and 0x0f].code.toByte()
        }
    }

    private fun checkedRandom(size: Int): ByteArray = primitives.randomBytes(size).also {
        check(it.size == size) { "The platform CSPRNG returned an invalid length" }
    }

    private fun envelopeAad(
        vaultId: String,
        envelopeId: String,
        wrapperType: KeyWrapperType,
        kdfAlgorithm: String,
    ): ByteArray = "mykeys:dek-wrap:v1|$vaultId|$envelopeId|${wrapperType.wireName}|$kdfAlgorithm".encodeToByteArray()

    private fun itemAad(vaultId: String, itemId: String, payloadSchemaVersion: Int): ByteArray =
        "mykeys:item:v1|$vaultId|$itemId|$payloadSchemaVersion".encodeToByteArray()

    private fun validateIdentifier(value: String, label: String) {
        if (!UUID_REGEX.matches(value)) {
            throw VaultCryptoException.InvalidInput("The $label identifier must be a canonical UUID")
        }
    }

    private inline fun <T> decryptAuthenticated(block: () -> T): T = try {
        block()
    } catch (known: VaultCryptoException) {
        throw known
    } catch (failure: Throwable) {
        throw VaultCryptoException.AuthenticationFailed(failure)
    }

    private companion object {
        val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
