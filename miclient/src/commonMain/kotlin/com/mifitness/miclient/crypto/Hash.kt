@file:OptIn(ExperimentalUnsignedTypes::class)

package com.mifitness.miclient.crypto

/**
 * Pure-Kotlin cryptographic hash functions used by Mi's signing protocol.
 *
 * These are standard implementations — not performance-critical (we hash
 * small payloads < 64 KB). Using pure Kotlin avoids platform expect/actual
 * for CommonCrypto/java.security.
 */
internal object Hash {
    fun sha256(input: ByteArray): ByteArray = SHA256().digest(input)
    fun sha1(input: ByteArray): ByteArray = SHA1().digest(input)
    fun md5(input: ByteArray): ByteArray = MD5().digest(input)
}

// --- SHA-256 ---

private class SHA256 {
    fun digest(message: ByteArray): ByteArray {
        val paddedMessage = pad(message, 64, bigEndianLength = true)
        var h0 = 0x6a09e667u; var h1 = 0xbb67ae85u; var h2 = 0x3c6ef372u; var h3 = 0xa54ff53au
        var h4 = 0x510e527fu; var h5 = 0x9b05688cu; var h6 = 0x1f83d9abu; var h7 = 0x5be0cd19u

        for (offset in paddedMessage.indices step 64) {
            val w = UIntArray(64)
            for (i in 0 until 16) w[i] = paddedMessage.getUIntBE(offset + i * 4)
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] shr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] shr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K256[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
        }
        return uintArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).toBytesBE()
    }
}

private val K256 = uintArrayOf(
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
)

// --- SHA-1 ---

private class SHA1 {
    fun digest(message: ByteArray): ByteArray {
        val paddedMessage = pad(message, 64, bigEndianLength = true)
        var h0 = 0x67452301u; var h1 = 0xefcdab89u; var h2 = 0x98badcfeu
        var h3 = 0x10325476u; var h4 = 0xc3d2e1f0u

        for (offset in paddedMessage.indices step 64) {
            val w = UIntArray(80)
            for (i in 0 until 16) w[i] = paddedMessage.getUIntBE(offset + i * 4)
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val (f, k) = when {
                    i < 20 -> ((b and c) or (b.inv() and d)) to 0x5a827999u
                    i < 40 -> (b xor c xor d) to 0x6ed9eba1u
                    i < 60 -> ((b and c) or (b and d) or (c and d)) to 0x8f1bbcdcu
                    else -> (b xor c xor d) to 0xca62c1d6u
                }
                val temp = a.rotateLeft(5) + f + e + k + w[i]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = temp
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        }
        return uintArrayOf(h0, h1, h2, h3, h4).toBytesBE()
    }
}

// --- MD5 ---

private class MD5 {
    fun digest(message: ByteArray): ByteArray {
        val paddedMessage = pad(message, 64, bigEndianLength = false)
        var a0 = 0x67452301u; var b0 = 0xefcdab89u; var c0 = 0x98badcfeu; var d0 = 0x10325476u

        for (offset in paddedMessage.indices step 64) {
            val m = UIntArray(16) { paddedMessage.getUIntLE(offset + it * 4) }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                val (f, g) = when {
                    i < 16 -> ((b and c) or (b.inv() and d)) to i
                    i < 32 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                    i < 48 -> (b xor c xor d) to ((3 * i + 5) % 16)
                    else -> (c xor (b or d.inv())) to ((7 * i) % 16)
                }
                val temp = d; d = c; c = b
                b = b + (a + f + MD5_K[i] + m[g]).rotateLeft(MD5_S[i])
                a = temp
            }
            a0 += a; b0 += b; c0 += c; d0 += d
        }
        return uintArrayOf(a0, b0, c0, d0).toBytesLE()
    }
}

private val MD5_S = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val MD5_K = UIntArray(64) { i ->
    (4294967296.0 * kotlin.math.abs(kotlin.math.sin((i + 1).toDouble()))).toUInt()
}

// --- Utility extensions ---

private fun pad(message: ByteArray, blockSize: Int, bigEndianLength: Boolean): ByteArray {
    val bitLength = message.size.toLong() * 8
    val padded = message.toMutableList()
    padded.add(0x80.toByte())
    while (padded.size % blockSize != blockSize - 8) padded.add(0)
    if (bigEndianLength) {
        for (shift in 56 downTo 0 step 8) padded.add((bitLength ushr shift).toByte())
    } else {
        for (shift in 0 until 64 step 8) padded.add((bitLength ushr shift).toByte())
    }
    return padded.toByteArray()
}

private fun ByteArray.getUIntBE(offset: Int): UInt =
    ((this[offset].toInt() and 0xFF).toUInt() shl 24) or
    ((this[offset + 1].toInt() and 0xFF).toUInt() shl 16) or
    ((this[offset + 2].toInt() and 0xFF).toUInt() shl 8) or
    (this[offset + 3].toInt() and 0xFF).toUInt()

private fun ByteArray.getUIntLE(offset: Int): UInt =
    (this[offset].toInt() and 0xFF).toUInt() or
    ((this[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
    ((this[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
    ((this[offset + 3].toInt() and 0xFF).toUInt() shl 24)

private fun UIntArray.toBytesBE(): ByteArray {
    val bytes = ByteArray(size * 4)
    for (i in indices) {
        bytes[i * 4] = (this[i] shr 24).toByte()
        bytes[i * 4 + 1] = (this[i] shr 16).toByte()
        bytes[i * 4 + 2] = (this[i] shr 8).toByte()
        bytes[i * 4 + 3] = this[i].toByte()
    }
    return bytes
}

private fun UIntArray.toBytesLE(): ByteArray {
    val bytes = ByteArray(size * 4)
    for (i in indices) {
        bytes[i * 4] = this[i].toByte()
        bytes[i * 4 + 1] = (this[i] shr 8).toByte()
        bytes[i * 4 + 2] = (this[i] shr 16).toByte()
        bytes[i * 4 + 3] = (this[i] shr 24).toByte()
    }
    return bytes
}

private fun UInt.rotateLeft(bits: Int): UInt = (this shl bits) or (this shr (32 - bits))
private fun UInt.rotateRight(bits: Int): UInt = (this shr bits) or (this shl (32 - bits))
