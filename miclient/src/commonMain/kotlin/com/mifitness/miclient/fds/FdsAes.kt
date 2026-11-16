package com.mifitness.miclient.fds

/**
 * AES/CBC/PKCS5 decrypt used for FDS object payloads
 * (`com.xiaomi.fit.fitness.persist.utils.AESCoder`).
 *
 * - Key: URL-safe base64 of 16 raw AES key bytes (`obj_key` from gen_download_url).
 * - IV: fixed ASCII `1234567887654321`.
 * - Ciphertext: URL-safe base64 body from the presigned download URL.
 */
object FdsAes {
    private val fixedIv = "1234567887654321".encodeToByteArray()

    fun decodeObjectKey(objectKey: String): ByteArray {
        val key = FdsKeys.decodeUrlSafe(objectKey)
        require(key.size == 16) { "AES key must be 16 bytes, got ${key.size}" }
        return key
    }

    fun decryptBase64Ciphertext(objectKey: String, base64Ciphertext: String): ByteArray {
        val key = decodeObjectKey(objectKey)
        // Some FDS objects (e.g. indoor workout records) come back as a JSON string: "<base64>"
        val ciphertext = FdsKeys.decodeUrlSafe(base64Ciphertext.trim().removeSurrounding("\""))
        return decrypt(key, ciphertext)
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 16)
        val padded = pkcs7Pad(plaintext)
        val aes = Aes128Ecb(key)
        val out = ByteArray(padded.size)
        var prev = fixedIv.copyOf()
        var offset = 0
        while (offset < padded.size) {
            val block = ByteArray(16)
            for (i in 0 until 16) {
                block[i] = (padded[offset + i].toInt() xor prev[i].toInt()).toByte()
            }
            val enc = aes.encryptBlock(block)
            enc.copyInto(out, offset)
            prev = enc
            offset += 16
        }
        return out
    }

    fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == 16) { "AES-128 key required" }
        require(ciphertext.isNotEmpty() && ciphertext.size % 16 == 0) {
            "ciphertext length ${ciphertext.size} is not a multiple of 16"
        }
        val aes = Aes128Ecb(key)
        val plain = ByteArray(ciphertext.size)
        var prev = fixedIv.copyOf()
        var offset = 0
        while (offset < ciphertext.size) {
            val block = ciphertext.copyOfRange(offset, offset + 16)
            val dec = aes.decryptBlock(block)
            for (i in 0 until 16) {
                plain[offset + i] = (dec[i].toInt() xor prev[i].toInt()).toByte()
            }
            prev = block
            offset += 16
        }
        return pkcs7Unpad(plain)
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        return data + ByteArray(pad) { pad.toByte() }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val pad = data.last().toInt() and 0xFF
        require(pad in 1..16) { "invalid PKCS7 pad $pad" }
        for (i in 1..pad) {
            require((data[data.size - i].toInt() and 0xFF) == pad) { "invalid PKCS7 padding" }
        }
        return data.copyOf(data.size - pad)
    }
}

/** Classic AES-128 ECB (single-block encrypt/decrypt). */
internal class Aes128Ecb(key: ByteArray) {
    private val w = IntArray(44)

    init {
        require(key.size == 16)
        for (i in 0 until 4) {
            w[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or
                ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or
                (key[4 * i + 3].toInt() and 0xFF)
        }
        for (i in 4 until 44) {
            var temp = w[i - 1]
            if (i % 4 == 0) {
                temp = subWord(rotWord(temp)) xor (RCON[i / 4] shl 24)
            }
            w[i] = w[i - 4] xor temp
        }
    }

    fun encryptBlock(input: ByteArray): ByteArray {
        val s = stateFrom(input)
        addRoundKey(s, 0)
        for (round in 1..9) {
            subBytes(s)
            shiftRows(s)
            mixColumns(s)
            addRoundKey(s, round)
        }
        subBytes(s)
        shiftRows(s)
        addRoundKey(s, 10)
        return stateToBytes(s)
    }

    fun decryptBlock(input: ByteArray): ByteArray {
        val s = stateFrom(input)
        addRoundKey(s, 10)
        for (round in 9 downTo 1) {
            invShiftRows(s)
            invSubBytes(s)
            addRoundKey(s, round)
            invMixColumns(s)
        }
        invShiftRows(s)
        invSubBytes(s)
        addRoundKey(s, 0)
        return stateToBytes(s)
    }

    private fun addRoundKey(s: IntArray, round: Int) {
        for (c in 0 until 4) {
            s[c] = s[c] xor w[round * 4 + c]
        }
    }

    private fun subBytes(s: IntArray) {
        for (c in 0 until 4) {
            s[c] = (SBOX[(s[c] ushr 24)] shl 24) or
                (SBOX[(s[c] ushr 16) and 0xFF] shl 16) or
                (SBOX[(s[c] ushr 8) and 0xFF] shl 8) or
                SBOX[s[c] and 0xFF]
        }
    }

    private fun invSubBytes(s: IntArray) {
        for (c in 0 until 4) {
            s[c] = (INV_SBOX[(s[c] ushr 24)] shl 24) or
                (INV_SBOX[(s[c] ushr 16) and 0xFF] shl 16) or
                (INV_SBOX[(s[c] ushr 8) and 0xFF] shl 8) or
                INV_SBOX[s[c] and 0xFF]
        }
    }

    private fun shiftRows(s: IntArray) {
        val t = s.copyOf()
        // row r shifts left by r; columns are words
        for (r in 1..3) {
            val bytes = IntArray(4) { c -> (t[c] ushr (24 - 8 * r)) and 0xFF }
            for (c in 0 until 4) {
                val b = bytes[(c + r) % 4]
                val shift = 24 - 8 * r
                s[c] = (s[c] and (0xFF shl shift).inv()) or (b shl shift)
            }
        }
    }

    private fun invShiftRows(s: IntArray) {
        val t = s.copyOf()
        for (r in 1..3) {
            val bytes = IntArray(4) { c -> (t[c] ushr (24 - 8 * r)) and 0xFF }
            for (c in 0 until 4) {
                val b = bytes[(c - r + 4) % 4]
                val shift = 24 - 8 * r
                s[c] = (s[c] and (0xFF shl shift).inv()) or (b shl shift)
            }
        }
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = (s[c] ushr 24) and 0xFF
            val a1 = (s[c] ushr 16) and 0xFF
            val a2 = (s[c] ushr 8) and 0xFF
            val a3 = s[c] and 0xFF
            val b0 = mul(a0, 2) xor mul(a1, 3) xor a2 xor a3
            val b1 = a0 xor mul(a1, 2) xor mul(a2, 3) xor a3
            val b2 = a0 xor a1 xor mul(a2, 2) xor mul(a3, 3)
            val b3 = mul(a0, 3) xor a1 xor a2 xor mul(a3, 2)
            s[c] = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
    }

    private fun invMixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = (s[c] ushr 24) and 0xFF
            val a1 = (s[c] ushr 16) and 0xFF
            val a2 = (s[c] ushr 8) and 0xFF
            val a3 = s[c] and 0xFF
            val b0 = mul(a0, 0x0e) xor mul(a1, 0x0b) xor mul(a2, 0x0d) xor mul(a3, 0x09)
            val b1 = mul(a0, 0x09) xor mul(a1, 0x0e) xor mul(a2, 0x0b) xor mul(a3, 0x0d)
            val b2 = mul(a0, 0x0d) xor mul(a1, 0x09) xor mul(a2, 0x0e) xor mul(a3, 0x0b)
            val b3 = mul(a0, 0x0b) xor mul(a1, 0x0d) xor mul(a2, 0x09) xor mul(a3, 0x0e)
            s[c] = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
    }

    private fun stateFrom(input: ByteArray): IntArray {
        val s = IntArray(4)
        for (c in 0 until 4) {
            s[c] = ((input[4 * c].toInt() and 0xFF) shl 24) or
                ((input[4 * c + 1].toInt() and 0xFF) shl 16) or
                ((input[4 * c + 2].toInt() and 0xFF) shl 8) or
                (input[4 * c + 3].toInt() and 0xFF)
        }
        return s
    }

    private fun stateToBytes(s: IntArray): ByteArray {
        val out = ByteArray(16)
        for (c in 0 until 4) {
            out[4 * c] = (s[c] ushr 24).toByte()
            out[4 * c + 1] = (s[c] ushr 16).toByte()
            out[4 * c + 2] = (s[c] ushr 8).toByte()
            out[4 * c + 3] = s[c].toByte()
        }
        return out
    }

    private fun subWord(x: Int): Int =
        (SBOX[(x ushr 24)] shl 24) or
            (SBOX[(x ushr 16) and 0xFF] shl 16) or
            (SBOX[(x ushr 8) and 0xFF] shl 8) or
            SBOX[x and 0xFF]

    private fun rotWord(x: Int): Int = (x shl 8) or (x ushr 24)

    private fun mul(a: Int, b: Int): Int {
        var aa = a
        var bb = b
        var p = 0
        repeat(8) {
            if ((bb and 1) != 0) p = p xor aa
            val hi = aa and 0x80
            aa = (aa shl 1) and 0xFF
            if (hi != 0) aa = aa xor 0x1B
            bb = bb ushr 1
        }
        return p
    }

    companion object {
        private val RCON = intArrayOf(
            0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36,
        )
        private val SBOX = intArrayOf(
            0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
            0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
            0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
            0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
            0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
            0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
            0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
            0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
            0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
            0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
            0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
            0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
            0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
            0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
            0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
            0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
        )
        private val INV_SBOX = IntArray(256).also { inv ->
            for (i in SBOX.indices) inv[SBOX[i]] = i
        }
    }
}
