package com.wanderwk.d3saveeditor.core

/**
 * Diablo III (Switch/offline) save file cipher: XOR stream with a 64-bit
 * key and a rotating feedback of the plaintext byte. Ported from the
 * reference Python implementation (protobuf_handler.py), itself based on
 * https://github.com/GoobyCorp/D3Edit.
 *
 * Both directions feed the *plaintext* byte back into the keystream state,
 * which is why encrypt/decrypt are exact inverses despite reading the
 * feedback byte at different points (post-xor for decrypt, pre-xor for
 * encrypt).
 */
object SaveCipher {
    private const val XOR_KEY = 0x305F92D82EC9A01BL

    fun decrypt(data: ByteArray): ByteArray {
        val out = data.copyOf()
        var num = XOR_KEY
        for (i in out.indices) {
            val orig = out[i].toInt() and 0xFF
            val newByte = orig xor (num and 0xFFL).toInt()
            out[i] = newByte.toByte()
            num = ((num xor newByte.toLong()) shl 56) or (num ushr 8)
        }
        return out
    }

    fun encrypt(data: ByteArray): ByteArray {
        val out = data.copyOf()
        var num = XOR_KEY
        for (i in out.indices) {
            val plain = out[i].toInt() and 0xFF
            val newByte = plain xor (num and 0xFFL).toInt()
            out[i] = newByte.toByte()
            num = ((num xor plain.toLong()) shl 56) or (num ushr 8)
        }
        return out
    }
}
