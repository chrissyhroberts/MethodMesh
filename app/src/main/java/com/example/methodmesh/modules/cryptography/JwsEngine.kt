package com.example.methodmesh.modules.cryptography

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.AlgorithmParameters
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

/** Dependency-free RFC 7515 ES256 detached JWS + RFC 7517/7638 JWK helpers. */
object JwsEngine {
    private val b64u = Base64.getUrlEncoder().withoutPadding()
    private val b64ud = Base64.getUrlDecoder()

    fun publicJwk(publicKey: ECPublicKey): String {
        val x = fixedUnsigned(publicKey.w.affineX, 32)
        val y = fixedUnsigned(publicKey.w.affineY, 32)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${b64u.encodeToString(x)}\",\"y\":\"${b64u.encodeToString(y)}\"}"
    }

    /** RFC 7638 SHA-256 JWK Thumbprint for an EC P-256 JWK. */
    fun thumbprint(jwk: String): String {
        val p = parseJwk(jwk)
        val canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"${p.x}\",\"y\":\"${p.y}\"}"
        return b64u.encodeToString(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    fun signDetached(data: ByteArray, privateKey: PrivateKey, publicJwk: String): String {
        val kid = thumbprint(publicJwk)
        val protectedJson = "{\"alg\":\"ES256\",\"kid\":\"$kid\",\"typ\":\"JWS\"}"
        val protected = b64u.encodeToString(protectedJson.toByteArray(StandardCharsets.UTF_8))
        val encodedPayload = b64u.encodeToString(data)
        val signingInput = "$protected.$encodedPayload".toByteArray(StandardCharsets.US_ASCII)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(signingInput)
        val raw = derToJose(signature.sign(), 32)
        return "$protected..${b64u.encodeToString(raw)}"
    }

    fun verifyDetached(data: ByteArray, compactDetachedJws: String, jwk: String): String {
        val parts = compactDetachedJws.trim().split('.')
        require(parts.size == 3 && parts[1].isEmpty()) { "Expected detached JWS Compact Serialization." }
        val header = String(b64ud.decode(parts[0]), StandardCharsets.UTF_8)
        require(Regex("\\\"alg\\\"\\s*:\\s*\\\"ES256\\\"").containsMatchIn(header)) { "Only ES256 is supported." }
        val publicKey = publicKeyFromJwk(jwk)
        val encodedPayload = b64u.encodeToString(data)
        val signingInput = "${parts[0]}.$encodedPayload".toByteArray(StandardCharsets.US_ASCII)
        val raw = b64ud.decode(parts[2])
        require(raw.size == 64) { "ES256 JWS signature must be 64 bytes." }
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signingInput)
        require(verifier.verify(joseToDer(raw))) { "JWS signature is invalid." }
        return thumbprint(jwk)
    }

    fun publicKeyFromJwk(jwk: String): ECPublicKey {
        val p = parseJwk(jwk)
        val x = BigInteger(1, b64ud.decode(p.x))
        val y = BigInteger(1, b64ud.decode(p.y))
        val algorithmParameters = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val params = algorithmParameters.getParameterSpec(ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    }

    private data class ParsedJwk(val x: String, val y: String)

    private fun parseJwk(jwk: String): ParsedJwk {
        fun field(name: String): String = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(jwk)?.groupValues?.get(1) ?: error("Missing JWK field: $name")
        require(field("kty") == "EC") { "Only EC JWK is supported." }
        require(field("crv") == "P-256") { "Only P-256 JWK is supported." }
        val x = field("x")
        val y = field("y")
        require(b64ud.decode(x).size == 32 && b64ud.decode(y).size == 32) { "P-256 JWK coordinates must be 32 bytes." }
        return ParsedJwk(x, y)
    }

    private fun fixedUnsigned(value: BigInteger, size: Int): ByteArray {
        val raw = value.toByteArray()
        val unsigned = if (raw.size > size && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(unsigned.size <= size)
        return ByteArray(size - unsigned.size) + unsigned
    }

    private fun derToJose(der: ByteArray, partSize: Int): ByteArray {
        var i = 0
        require((der[i++].toInt() and 0xff) == 0x30)
        val (_, seqLenBytes) = readLengthBytes(der, i); i += seqLenBytes
        require((der[i++].toInt() and 0xff) == 0x02)
        val (rLen, rLenBytes) = readLengthBytes(der, i); i += rLenBytes
        val r = der.copyOfRange(i, i + rLen); i += rLen
        require((der[i++].toInt() and 0xff) == 0x02)
        val (sLen, sLenBytes) = readLengthBytes(der, i); i += sLenBytes
        val s = der.copyOfRange(i, i + sLen)
        return unsignedPart(r, partSize) + unsignedPart(s, partSize)
    }

    private fun joseToDer(raw: ByteArray): ByteArray {
        require(raw.size % 2 == 0)
        val n = raw.size / 2
        val r = positiveDerInt(raw.copyOfRange(0, n))
        val s = positiveDerInt(raw.copyOfRange(n, raw.size))
        val body = byteArrayOf(0x02) + encodeLength(r.size) + r + byteArrayOf(0x02) + encodeLength(s.size) + s
        return byteArrayOf(0x30) + encodeLength(body.size) + body
    }

    private fun unsignedPart(v: ByteArray, size: Int): ByteArray {
        var start = 0
        while (start < v.size - 1 && v[start] == 0.toByte()) start++
        val clean = v.copyOfRange(start, v.size)
        require(clean.size <= size)
        return ByteArray(size - clean.size) + clean
    }

    private fun positiveDerInt(v: ByteArray): ByteArray {
        var start = 0
        while (start < v.size - 1 && v[start] == 0.toByte()) start++
        val clean = v.copyOfRange(start, v.size)
        return if ((clean[0].toInt() and 0x80) != 0) byteArrayOf(0) + clean else clean
    }

    private fun readLengthBytes(data: ByteArray, offset: Int): Pair<Int, Int> {
        val first = data[offset].toInt() and 0xff
        if (first < 0x80) return first to 1
        val count = first and 0x7f
        require(count in 1..2)
        var len = 0
        repeat(count) { len = (len shl 8) or (data[offset + 1 + it].toInt() and 0xff) }
        return len to (count + 1)
    }

    private fun encodeLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length <= 0xff -> byteArrayOf(0x81.toByte(), length.toByte())
        else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
    }
}
