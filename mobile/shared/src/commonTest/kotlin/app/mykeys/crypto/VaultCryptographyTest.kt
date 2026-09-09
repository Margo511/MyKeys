package app.mykeys.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VaultCryptographyTest {
    private val primitives = TestPrimitives()
    private val crypto = VaultCryptography(primitives)

    @Test
    fun setupCreatesIndependentEnvelopesThatUnlockTheSameDek() {
        val setup = createSetup()
        val fromMaster = crypto.unlockWithMasterPassword(setup.masterPasswordEnvelope, MASTER_PASSWORD)
        val fromRecovery = crypto.unlockWithRecoveryKey(setup.recoveryKeyEnvelope, setup.recoveryPhrase)
        val plaintext = "{\"name\":\"correo\",\"password\":\"secreto\"}".encodeToByteArray()

        val encrypted = crypto.encryptItem(fromMaster, VAULT_ID, ITEM_ID, 1, plaintext)

        assertContentEquals(plaintext, crypto.decryptItem(fromRecovery, encrypted))
        assertEquals(24, setup.recoveryPhrase.split(' ').size)
        assertFalse(setup.masterPasswordEnvelope.wrappedDek.contentEquals(setup.recoveryKeyEnvelope.wrappedDek))
        setup.unlockedKey.destroy()
        fromMaster.destroy()
        fromRecovery.destroy()
    }

    @Test
    fun wrongMasterPasswordFailsClosed() {
        val setup = createSetup()

        assertFailsWith<VaultCryptoException.AuthenticationFailed> {
            crypto.unlockWithMasterPassword(setup.masterPasswordEnvelope, "una contraseña distinta 9!")
        }
        setup.unlockedKey.destroy()
    }

    @Test
    fun corruptionAndContextSubstitutionFailAuthentication() {
        val setup = createSetup()
        val encrypted = crypto.encryptItem(
            setup.unlockedKey,
            VAULT_ID,
            ITEM_ID,
            1,
            "contenido privado".encodeToByteArray(),
        )
        val corruptedBytes = encrypted.ciphertextAndTag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertFailsWith<VaultCryptoException.AuthenticationFailed> {
            crypto.decryptItem(setup.unlockedKey, encrypted.copy(ciphertextAndTag = corruptedBytes))
        }
        assertFailsWith<VaultCryptoException.AuthenticationFailed> {
            crypto.decryptItem(setup.unlockedKey, encrypted.copy(itemId = OTHER_ITEM_ID))
        }
        setup.unlockedKey.destroy()
    }

    @Test
    fun everyEncryptionUsesANewNonce() {
        val setup = createSetup()

        val first = crypto.encryptItem(
            setup.unlockedKey,
            VAULT_ID,
            ITEM_ID,
            1,
            "primero".encodeToByteArray(),
        )
        val second = crypto.encryptItem(
            setup.unlockedKey,
            VAULT_ID,
            OTHER_ITEM_ID,
            1,
            "segundo".encodeToByteArray(),
        )

        assertFalse(first.nonce.contentEquals(second.nonce))
        setup.unlockedKey.destroy()
    }

    @Test
    fun unapprovedRemoteKdfParametersAreRejectedBeforeUnlock() {
        val setup = createSetup()
        val altered = setup.masterPasswordEnvelope.copy(
            kdfParameters = setup.masterPasswordEnvelope.kdfParameters.copy(memoryKiB = 131_072),
        )

        assertFailsWith<VaultCryptoException.UnsupportedFormat> {
            crypto.unlockWithMasterPassword(altered, MASTER_PASSWORD)
        }
        setup.unlockedKey.destroy()
    }

    @Test
    fun rotatingMasterPasswordKeepsTheDekAndInvalidatesTheOldPassword() {
        val setup = createSetup()
        val rotated = crypto.rotateMasterPassword(
            setup.masterPasswordEnvelope,
            MASTER_PASSWORD,
            NEW_MASTER_ENVELOPE_ID,
            NEW_MASTER_PASSWORD,
        )

        val key = crypto.unlockWithMasterPassword(rotated, NEW_MASTER_PASSWORD)
        assertFailsWith<VaultCryptoException.AuthenticationFailed> {
            crypto.unlockWithMasterPassword(rotated, MASTER_PASSWORD)
        }
        assertNotEquals(setup.masterPasswordEnvelope.id, rotated.id)
        key.destroy()
        setup.unlockedKey.destroy()
    }

    @Test
    fun rotatingRecoveryKeyProducesANewCheckedPhrase() {
        val setup = createSetup()
        val rotated = crypto.rotateRecoveryKey(
            setup.recoveryKeyEnvelope,
            setup.recoveryPhrase,
            NEW_RECOVERY_ENVELOPE_ID,
        )

        val key = crypto.unlockWithRecoveryKey(rotated.envelope, rotated.recoveryPhrase)
        assertFailsWith<VaultCryptoException.AuthenticationFailed> {
            crypto.unlockWithRecoveryKey(rotated.envelope, setup.recoveryPhrase)
        }
        assertNotEquals(setup.recoveryPhrase, rotated.recoveryPhrase)
        key.destroy()
        setup.unlockedKey.destroy()
    }

    @Test
    fun recoveryKeyRejectsUnknownWordAndBadChecksum() {
        val setup = createSetup()
        val words = setup.recoveryPhrase.split(' ').toMutableList()
        words[0] = "zzzzz"
        assertFailsWith<VaultCryptoException.InvalidInput> {
            crypto.unlockWithRecoveryKey(setup.recoveryKeyEnvelope, words.joinToString(" "))
        }

        val validWords = setup.recoveryPhrase.split(' ').toMutableList()
        val lastWord = validWords[23]
        validWords[23] = lastWord.dropLast(1) + if (lastWord.last() == 'b') "d" else "b"
        assertFailsWith<VaultCryptoException.InvalidInput> {
            crypto.unlockWithRecoveryKey(setup.recoveryKeyEnvelope, validWords.joinToString(" "))
        }
        setup.unlockedKey.destroy()
    }

    @Test
    fun destroyedDekCannotBeUsedAgain() {
        val setup = createSetup()
        setup.unlockedKey.destroy()

        assertTrue(setup.unlockedKey.isDestroyed)
        assertFailsWith<IllegalStateException> {
            crypto.encryptItem(setup.unlockedKey, VAULT_ID, ITEM_ID, 1, byteArrayOf(1))
        }
    }

    private fun createSetup() = crypto.createVaultKeyMaterial(
        vaultId = VAULT_ID,
        masterEnvelopeId = MASTER_ENVELOPE_ID,
        recoveryEnvelopeId = RECOVERY_ENVELOPE_ID,
        masterPassword = MASTER_PASSWORD,
    )

    private companion object {
        const val VAULT_ID = "10000000-0000-4000-8000-000000000001"
        const val ITEM_ID = "20000000-0000-4000-8000-000000000001"
        const val OTHER_ITEM_ID = "20000000-0000-4000-8000-000000000002"
        const val MASTER_ENVELOPE_ID = "30000000-0000-4000-8000-000000000001"
        const val RECOVERY_ENVELOPE_ID = "30000000-0000-4000-8000-000000000002"
        const val NEW_MASTER_ENVELOPE_ID = "30000000-0000-4000-8000-000000000003"
        const val NEW_RECOVERY_ENVELOPE_ID = "30000000-0000-4000-8000-000000000004"
        const val MASTER_PASSWORD = "maestra segura 2026!"
        const val NEW_MASTER_PASSWORD = "otra maestra segura 2027!"
    }
}

private class TestPrimitives : VaultCryptoPrimitives {
    private var counter = 1

    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { (counter++ and 0xff).toByte() }

    override fun sha256(input: ByteArray): ByteArray = digest(input)

    override fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: Argon2idParameters,
    ): ByteArray = digest(password + salt).copyOf(parameters.outputBytes)

    override fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputBytes: Int,
    ): ByteArray = digest(inputKeyMaterial + salt + info).copyOf(outputBytes)

    override fun aes256GcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val body = ByteArray(plaintext.size) { index ->
            (plaintext[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
        return body + digest(key + nonce + associatedData + body).copyOf(GCM_TAG_BYTES)
    }

    override fun aes256GcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val body = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - GCM_TAG_BYTES)
        val tag = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - GCM_TAG_BYTES, ciphertextAndTag.size)
        val expected = digest(key + nonce + associatedData + body).copyOf(GCM_TAG_BYTES)
        if (!tag.contentEquals(expected)) throw SecurityException("authentication failed")
        return ByteArray(body.size) { index ->
            (body[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
    }

    private fun digest(input: ByteArray): ByteArray {
        var state = 0x6d2b79f5
        input.forEach { state = (state xor (it.toInt() and 0xff)) * 0x45d9f3b }
        return ByteArray(32) { index ->
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            (state ushr ((index % 4) * 8)).toByte()
        }
    }
}
