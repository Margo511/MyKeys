package app.mykeys.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals

class VaultCryptoPrimitivesTest {
    private val primitives = createPlatformVaultCryptoPrimitives()

    @Test
    fun hkdfSha256MatchesRfc5869CaseOne() {
        val actual = primitives.hkdfSha256(
            inputKeyMaterial = ByteArray(22) { 0x0b },
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            outputBytes = 42,
        )

        assertContentEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            actual,
        )
    }

    @Test
    fun aes256GcmMatchesNistVector() {
        val key = ByteArray(32)
        val nonce = ByteArray(12)
        val plaintext = ByteArray(16)
        val expected = hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919")

        val encrypted = primitives.aes256GcmEncrypt(key, nonce, plaintext, ByteArray(0))

        assertContentEquals(expected, encrypted)
        assertContentEquals(plaintext, primitives.aes256GcmDecrypt(key, nonce, encrypted, ByteArray(0)))
    }

    @Test
    fun argon2idMatchesIndependentReferenceVector() {
        val actual = primitives.argon2id(
            password = "opensesame".encodeToByteArray(),
            salt = hex("bae908b0b4bacba7adbec828376d2456"),
            parameters = Argon2idParameters(
                memoryKiB = 12_288,
                iterations = 3,
                parallelism = 1,
                outputBytes = 32,
            ),
        )

        assertContentEquals(
            hex("7b132f0d7fcff5ec6f10f9a764bda0642951c8c76b9ea8eacb2b0b30ffd55160"),
            actual,
        )
    }
}

internal fun hex(value: String): ByteArray {
    require(value.length % 2 == 0)
    return ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

