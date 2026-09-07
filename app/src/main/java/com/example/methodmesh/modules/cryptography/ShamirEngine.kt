package com.example.methodmesh.modules.cryptography

import java.security.SecureRandom

/**
 * Shamir secret sharing over GF(256). The mathematical scheme is standard;
 * `mms1:` is only MethodMesh's transport encoding for a share, not an encryption format.
 */
data class ShamirShare(val threshold: Int, val index: Int, val payload: ByteArray) {
    fun encode(): String = "mms1:$threshold:$index:${with(CryptoEncoding) { payload.base64Url() }}"

    companion object {
        fun decode(value: String): ShamirShare {
            val parts = value.trim().split(':', limit = 4)
            require(parts.size == 4 && parts[0] == "mms1") { "Invalid MethodMesh Shamir share." }
            val threshold = parts[1].toInt()
            val index = parts[2].toInt()
            val payload = CryptoEncoding.base64UrlDecode(parts[3])
            return ShamirShare(threshold, index, payload)
        }
    }
}

object ShamirEngine {
    private val rng = SecureRandom()

    fun split(secret: ByteArray, threshold: Int, shares: Int): List<ShamirShare> {
        require(secret.isNotEmpty())
        require(threshold in 2..255)
        require(shares in threshold..255)

        val output = (1..shares).associateWith { ByteArray(secret.size) }.toMutableMap()
        secret.indices.forEach { byteIndex ->
            val coefficients = ByteArray(threshold)
            coefficients[0] = secret[byteIndex]
            if (threshold > 1) {
                val random = ByteArray(threshold - 1).also(rng::nextBytes)
                random.copyInto(coefficients, 1)
            }
            for (x in 1..shares) {
                var y = coefficients.last().toInt() and 0xff
                for (i in coefficients.size - 2 downTo 0) {
                    y = gfMul(y, x) xor (coefficients[i].toInt() and 0xff)
                }
                output.getValue(x)[byteIndex] = y.toByte()
            }
        }
        return output.map { (index, bytes) -> ShamirShare(threshold, index, bytes) }
    }

    fun combine(shares: List<ShamirShare>): ByteArray {
        require(shares.isNotEmpty())
        val threshold = shares.first().threshold
        require(shares.size >= threshold) { "Need at least $threshold shares." }
        require(shares.map { it.threshold }.distinct().size == 1) { "Shares have different thresholds." }
        require(shares.map { it.index }.distinct().size == shares.size) { "Duplicate share index." }
        require(shares.map { it.payload.size }.distinct().size == 1) { "Shares have different lengths." }

        val selected = shares.take(threshold)
        val result = ByteArray(selected.first().payload.size)
        result.indices.forEach { byteIndex ->
            var value = 0
            for (i in selected.indices) {
                val xi = selected[i].index
                val yi = selected[i].payload[byteIndex].toInt() and 0xff
                var basis = 1
                for (j in selected.indices) {
                    if (i == j) continue
                    val xj = selected[j].index
                    basis = gfMul(basis, gfDiv(xj, xj xor xi))
                }
                value = value xor gfMul(yi, basis)
            }
            result[byteIndex] = value.toByte()
        }
        return result
    }

    private fun gfMul(a: Int, b: Int): Int {
        var aa = a and 0xff
        var bb = b and 0xff
        var result = 0
        while (bb != 0) {
            if ((bb and 1) != 0) result = result xor aa
            val high = aa and 0x80
            aa = (aa shl 1) and 0xff
            if (high != 0) aa = aa xor 0x1b
            bb = bb ushr 1
        }
        return result
    }

    private fun gfPow(a: Int, exponent: Int): Int {
        var base = a
        var exp = exponent
        var result = 1
        while (exp > 0) {
            if ((exp and 1) != 0) result = gfMul(result, base)
            base = gfMul(base, base)
            exp = exp ushr 1
        }
        return result
    }

    private fun gfInv(a: Int): Int {
        require(a != 0) { "Cannot invert zero in GF(256)." }
        return gfPow(a, 254)
    }

    private fun gfDiv(a: Int, b: Int): Int = if (a == 0) 0 else gfMul(a, gfInv(b))
}
