package app.mykeys.crypto

/**
 * My Keys RK1 format: 256 random bits plus the first SHA-256 checksum byte, encoded as
 * 24 symbols of 11 bits. The 2,048 fixed five-letter symbols are generated rather than stored.
 */
class RecoveryKeyCodec(private val primitives: VaultCryptoPrimitives) {
    fun encode(seed: ByteArray): String {
        if (seed.size != RECOVERY_SEED_BYTES) {
            throw VaultCryptoException.InvalidInput("A recovery seed must contain 32 bytes")
        }
        val digest = primitives.sha256(seed).also { require(it.size == 32) }
        val checksum = digest[0]
        digest.wipe()
        val payload = seed + checksum
        return try {
            bytesToIndices(payload).joinToString(" ") { indexToWord(it) }
        } finally {
            payload.wipe()
        }
    }

    fun decode(phrase: String): ByteArray {
        val words = phrase
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
        if (words.size != RECOVERY_WORDS) {
            throw VaultCryptoException.InvalidInput("A recovery key must contain 24 words")
        }
        val payload = indicesToBytes(words.map(::wordToIndex))
        return try {
            val seed = payload.copyOfRange(0, RECOVERY_SEED_BYTES)
            val digest = try {
                primitives.sha256(seed).also { require(it.size == 32) }
            } catch (failure: Throwable) {
                seed.wipe()
                throw failure
            }
            val expected = digest[0].toInt() and 0xff
            digest.wipe()
            val actual = payload.last().toInt() and 0xff
            if (!constantTimeByteEquals(expected, actual)) {
                seed.wipe()
                throw VaultCryptoException.InvalidInput("The recovery key checksum is invalid")
            }
            seed
        } finally {
            payload.wipe()
        }
    }

    private fun bytesToIndices(bytes: ByteArray): List<Int> {
        val result = ArrayList<Int>(RECOVERY_WORDS)
        var accumulator = 0
        var bits = 0
        bytes.forEach { byte ->
            accumulator = (accumulator shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= WORD_BITS) {
                bits -= WORD_BITS
                result += (accumulator shr bits) and WORD_MASK
            }
        }
        check(bits == 0 && result.size == RECOVERY_WORDS)
        return result
    }

    private fun indicesToBytes(indices: List<Int>): ByteArray {
        val result = ByteArray(RECOVERY_PAYLOAD_BYTES)
        var outputIndex = 0
        var accumulator = 0
        var bits = 0
        indices.forEach { index ->
            accumulator = (accumulator shl WORD_BITS) or index
            bits += WORD_BITS
            while (bits >= 8) {
                bits -= 8
                result[outputIndex++] = ((accumulator shr bits) and 0xff).toByte()
            }
        }
        check(bits == 0 && outputIndex == result.size)
        return result
    }

    private fun indexToWord(index: Int): String {
        require(index in 0..WORD_MASK)
        val onset = ONSETS[index / 64]
        val nucleus = NUCLEI[(index / 8) % 8]
        val coda = CODAS[index % 8]
        return onset + nucleus + coda
    }

    private fun wordToIndex(word: String): Int {
        if (word.length != 5) {
            throw VaultCryptoException.InvalidInput("The recovery key contains an unknown word")
        }
        val onset = ONSETS.indexOf(word.substring(0, 2))
        val nucleus = NUCLEI.indexOf(word.substring(2, 4))
        val coda = CODAS.indexOf(word.substring(4, 5))
        if (onset < 0 || nucleus < 0 || coda < 0) {
            throw VaultCryptoException.InvalidInput("The recovery key contains an unknown word")
        }
        return onset * 64 + nucleus * 8 + coda
    }

    private fun constantTimeByteEquals(left: Int, right: Int): Boolean = (left xor right) == 0

    private companion object {
        const val RECOVERY_SEED_BYTES = 32
        const val RECOVERY_PAYLOAD_BYTES = 33
        const val RECOVERY_WORDS = 24
        const val WORD_BITS = 11
        const val WORD_MASK = 0x7ff

        val ONSETS = listOf(
            "ba", "be", "bi", "bo", "bu", "ca", "ce", "ci",
            "co", "cu", "da", "de", "di", "do", "du", "fa",
            "fe", "fi", "fo", "fu", "ga", "ge", "gi", "go",
            "gu", "la", "le", "li", "lo", "lu", "ma", "me",
        )
        val NUCLEI = listOf("ra", "re", "ri", "ro", "ru", "sa", "se", "si")
        val CODAS = listOf("b", "d", "f", "g", "k", "m", "n", "t")
    }
}
