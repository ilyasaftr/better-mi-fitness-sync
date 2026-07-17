package com.mifitness.miclient.crypto

/**
 * RC4 stream cipher with Xiaomi's 1024-byte keystream skip.
 *
 * Mi's variant discards the first 1024 keystream bytes before encrypting,
 * mitigating the well-known RC4 initial-byte bias. This class encapsulates
 * that behavior — construct with the key, then call [process] to encrypt
 * or decrypt (RC4 is symmetric).
 */
internal class SkippedRC4(key: ByteArray) {
    private val state = IntArray(256)
    private var i = 0
    private var j = 0

    init {
        initializeState(key)
        skipKeystream(bytes = 1024)
    }

    /** Encrypts or decrypts [input] in place and returns the result. */
    fun process(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        for (index in input.indices) {
            output[index] = (input[index].toInt() xor nextKeystreamByte()).toByte()
        }
        return output
    }

    private fun initializeState(key: ByteArray) {
        for (k in 0 until 256) state[k] = k
        var swap = 0
        for (k in 0 until 256) {
            swap = (swap + state[k] + (key[k % key.size].toInt() and 0xFF)) and 0xFF
            val temp = state[k]
            state[k] = state[swap]
            state[swap] = temp
        }
    }

    private fun skipKeystream(bytes: Int) {
        repeat(bytes) { nextKeystreamByte() }
    }

    private fun nextKeystreamByte(): Int {
        i = (i + 1) and 0xFF
        j = (j + state[i]) and 0xFF
        val temp = state[i]
        state[i] = state[j]
        state[j] = temp
        return state[(state[i] + state[j]) and 0xFF]
    }
}
