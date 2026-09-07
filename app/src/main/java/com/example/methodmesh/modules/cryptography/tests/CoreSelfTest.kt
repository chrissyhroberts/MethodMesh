import com.example.methodmesh.modules.cryptography.*
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.random.Random

private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun hx(b: ByteArray) = b.joinToString("") { "%02X".format(it) }

fun main() {
    // RFC 3394 section 4.1 known-answer vector.
    val kek = hex("000102030405060708090A0B0C0D0E0F")
    val key = hex("00112233445566778899AABBCCDDEEFF")
    val expectedWrap = "1FA68B0A8112B447AEF34BD8FB5A7B829D3E862371D2CFE5"
    val wrapped = AesKeyWrap.wrap(kek, key)
    check(hx(wrapped) == expectedWrap)
    check(AesKeyWrap.unwrap(kek, wrapped).contentEquals(key))

    // JWE profile self round-trip + authentication failure.
    val password = "correct horse battery staple".toCharArray()
    val message = "MethodMesh standalone JWE test ✓".toByteArray(Charsets.UTF_8)
    val jwe = JweEngine.encrypt(message, password, p2c = 10_000)
    check(JweEngine.decrypt(jwe, password).contentEquals(message))
    check(runCatching { JweEngine.decrypt(jwe, "wrong".toCharArray()) }.isFailure)
    val inspected = JweEngine.inspect(jwe)
    check(inspected.algorithm == JweEngine.ALG && inspected.encryption == JweEngine.ENC)

    // RFC 6238 SHA-1 test vector at T=59 seconds.
    val totp = TotpEngine.generate(
        TotpAccount("RFC", "test", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", digits = 8, periodSeconds = 30, algorithm = "SHA1"),
        epochSeconds = 59
    )
    check(totp.code == "94287082") { "TOTP vector was ${totp.code}" }

    // Password/token invariants.
    check(PasswordEngine.random(32).value.length == 32)
    check(PasswordEngine.pin(8).value.all(Char::isDigit))
    check(PasswordEngine.token(32).entropyBits == 256.0)

    // Shamir 3-of-5 randomized recovery.
    repeat(100) {
        val secret = ByteArray(48).also { Random.nextBytes(it) }
        val shares = ShamirEngine.split(secret, 3, 5)
        check(ShamirEngine.combine(listOf(shares[0], shares[2], shares[4])).contentEquals(secret))
    }

    // Pure JCA ES256/JWK/JWS round-trip and tamper rejection.
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    val pair = generator.generateKeyPair()
    val jwk = JwsEngine.publicJwk(pair.public as ECPublicKey)
    val thumbprint = JwsEngine.thumbprint(jwk)
    val jws = JwsEngine.signDetached(message, pair.private, jwk)
    check(JwsEngine.verifyDetached(message, jws, jwk) == thumbprint)
    check(runCatching { JwsEngine.verifyDetached("tampered".toByteArray(), jws, jwk) }.isFailure)

    println("PASS")
    println("JWE=$jwe")
    println("JWK=$jwk")
    println("JWK_THUMBPRINT=$thumbprint")
    println("JWS=$jws")
}
