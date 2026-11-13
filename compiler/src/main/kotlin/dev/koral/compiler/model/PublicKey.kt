package dev.koral.compiler.model

import java.math.BigInteger

/** Compile-time Base58 decoding only. Public keys are exactly 32 bytes. */
object PublicKey {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    fun decode(value: String): ByteArray {
        require(value.length in 32..44) { "Public key must encode exactly 32 bytes in Base58" }
        var number = BigInteger.ZERO
        for (character in value) {
            val digit = ALPHABET.indexOf(character)
            require(digit >= 0) { "Public key contains an invalid Base58 character" }
            number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
        }
        val significant = if (number == BigInteger.ZERO) byteArrayOf() else number.toByteArray().let {
            if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }
        val zeros = value.takeWhile { it == '1' }.length
        require(zeros + significant.size == 32) { "Public key must encode exactly 32 bytes in Base58" }
        return ByteArray(32).also { significant.copyInto(it, zeros) }
    }
}
