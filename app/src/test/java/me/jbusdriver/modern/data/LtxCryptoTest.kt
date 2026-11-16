package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.ltx.LtxCryptoSession
import me.jbusdriver.modern.data.ltx.LtxEncryptedEnvelopeDto
import me.jbusdriver.modern.data.ltx.LtxSessionHandshakeDto
import me.jbusdriver.modern.data.ltx.base64UrlDecode
import me.jbusdriver.modern.data.ltx.base64UrlEncode
import me.jbusdriver.modern.data.ltx.decryptLtxEnvelope
import me.jbusdriver.modern.data.ltx.establishLtxSession
import me.jbusdriver.modern.data.ltx.hkdfSha256
import me.jbusdriver.modern.data.ltx.importLtxServerPublicKey
import me.jbusdriver.modern.data.ltx.ltxPublicKeyRawBase64Url
import me.jbusdriver.modern.data.ltx.ltxResponseAad
import me.jbusdriver.modern.data.ltx.newLtxClientKeyPair
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.interfaces.ECPublicKey
import java.security.SecureRandom
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密会话协议纯函数测试：HKDF 以 RFC 5869 官方向量锁定实现正确性；
 * 会话建立与信封解密用「测试内独立复算 + JCE 加密回环」双重验证，
 * 确保与站点 SPA 的 WebCrypto 构造逐位一致。
 */
class LtxCryptoTest {

    private fun hex(text: String): ByteArray =
        text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // region HKDF（RFC 5869 Test Case 1, SHA-256）

    @Test
    fun `hkdf matches rfc5869 test case 1`() {
        val okm = hkdfSha256(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            outputLength = 42,
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHexString(),
        )
    }

    @Test
    fun `hkdf with empty salt uses zero prk key`() {
        // RFC 5869：salt 为空时以 HashLen 个零字节作为 HMAC 密钥
        val withEmpty = hkdfSha256(hex("0b"), ByteArray(0), ByteArray(0), 32)
        val withZeros = hkdfSha256(hex("0b"), ByteArray(32), ByteArray(0), 32)
        assertArrayEquals(withZeros, withEmpty)
    }

    // endregion

    // region base64url

    @Test
    fun `base64url roundtrips without padding`() {
        for (len in listOf(1, 16, 32, 65)) {
            val bytes = ByteArray(len) { (it % 251).toByte() }
            val encoded = base64UrlEncode(bytes)
            assertTrue(encoded.none { it == '=' || it == '+' || it == '/' })
            assertArrayEquals(bytes, base64UrlDecode(encoded))
        }
    }

    @Test
    fun `base64url decode rejects invalid input`() {
        assertNull(base64UrlDecode(""))
        assertNull(base64UrlDecode("a")) // len % 4 == 1
        assertNull(base64UrlDecode("ab+d")) // 非法字符
        assertNull(base64UrlDecode("ab/c"))
        assertNull(base64UrlDecode("ab?d"))
    }

    // endregion

    // region 会话建立与信封解密回环

    /** 构造一次完整握手：返回（客户端密钥对, 握手响应 DTO, 测试内独立复算的期望 AES 密钥）。 */
    private fun handshake(): Triple<java.security.KeyPair, LtxSessionHandshakeDto, ByteArray> {
        val client = newLtxClientKeyPair()
        val server = newLtxClientKeyPair()
        val sid = "test-sid-0001"
        val salt = ByteArray(32) { (it * 7).toByte() }
        val expiresAt = System.currentTimeMillis() + 3_600_000

        // 测试内独立复算服务端视角的派生密钥（不经被测函数）
        val clientRaw = base64UrlDecode(ltxPublicKeyRawBase64Url(client))!!
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(server.private)
        agreement.doPhase(importLtxServerPublicKey(clientRaw, (server.public as ECPublicKey).params), true)
        val secret = agreement.generateSecret()
        val shared = ByteArray(32)
        System.arraycopy(secret, 0, shared, 32 - secret.size, secret.size)
        val expectedKey = hkdfSha256(
            ikm = shared,
            salt = salt,
            info = "ltx-api-response-v1:$sid".toByteArray(StandardCharsets.UTF_8),
            outputLength = 32,
        )
        val dto = LtxSessionHandshakeDto(
            v = 1,
            sid = sid,
            publicKey = ltxPublicKeyRawBase64Url(server),
            salt = base64UrlEncode(salt),
            expiresAt = expiresAt,
        )
        return Triple(client, dto, expectedKey)
    }

    @Test
    fun `session establishment derives expected key`() {
        val (client, dto, expectedKey) = handshake()
        val session = establishLtxSession(client, dto)
        assertEquals("test-sid-0001", session.sid)
        assertArrayEquals(expectedKey, session.aesKey.encoded)
        assertEquals(dto.expiresAt, session.expiresAtMillis)
    }

    @Test
    fun `session establishment rejects malformed handshake`() {
        val (client, dto) = handshake()
        // 版本不符 / 缺 sid / 已过期 / 公钥长度错 / salt 长度错
        assertEstablishFails(client, dto.copy(v = 2))
        assertEstablishFails(client, dto.copy(sid = null))
        assertEstablishFails(client, dto.copy(expiresAt = System.currentTimeMillis() - 1))
        assertEstablishFails(client, dto.copy(publicKey = base64UrlEncode(ByteArray(64))))
        assertEstablishFails(client, dto.copy(salt = base64UrlEncode(ByteArray(31))))
    }

    private fun assertEstablishFails(client: java.security.KeyPair, dto: LtxSessionHandshakeDto) {
        try {
            establishLtxSession(client, dto)
            fail("expected establishment failure for $dto")
        } catch (expected: IllegalArgumentException) {
        } catch (expected: IllegalStateException) {
        }
    }

    /** 用派生密钥按站点构造方式加密出完整信封（JCE 视角的服务端）。 */
    private fun encryptEnvelope(
        session: LtxCryptoSession,
        plaintext: ByteArray,
        rid: String,
        method: String,
        pathAndSearch: String,
        httpStatus: Int,
        encoding: String,
    ): LtxEncryptedEnvelopeDto {
        val payload = if (encoding == "gzip") gzip(plaintext) else plaintext
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, session.aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(ltxResponseAad(session.sid, rid, method, pathAndSearch, httpStatus, encoding))
        val ct = cipher.doFinal(payload) // 明文||tag，与 WebCrypto 布局一致
        return LtxEncryptedEnvelopeDto(
            v = 1,
            sid = session.sid,
            rid = rid,
            iv = base64UrlEncode(iv),
            body = base64UrlEncode(ct),
            encoding = encoding,
        )
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(data) }
            out.toByteArray()
        }

    @Test
    fun `envelope decrypt roundtrip identity and gzip`() {
        val (client, dto, _) = handshake()
        val session = establishLtxSession(client, dto)
        val rid = "request-id-0001"
        val path = "/api/items"
        val plaintext = """{"items":[{"code":"TEST-001"}],"updated_at":1}"""
            .toByteArray(StandardCharsets.UTF_8)

        // identity
        val identityEnvelope = encryptEnvelope(session, plaintext, rid, "GET", path, 200, "identity")
        assertEquals(
            plaintext.toString(StandardCharsets.UTF_8),
            decryptLtxEnvelope(identityEnvelope, session, "GET", path, 200),
        )
        // gzip
        val gzipEnvelope = encryptEnvelope(session, plaintext, rid, "GET", path, 200, "gzip")
        assertEquals(
            plaintext.toString(StandardCharsets.UTF_8),
            decryptLtxEnvelope(gzipEnvelope, session, "GET", path, 200),
        )
    }

    @Test
    fun `envelope decrypt fails on aad mismatch`() {
        val (client, dto, _) = handshake()
        val session = establishLtxSession(client, dto)
        val plaintext = "payload".toByteArray(StandardCharsets.UTF_8)
        val envelope = encryptEnvelope(session, plaintext, "rid-1", "GET", "/api/items", 200, "identity")
        try {
            // path/status/method 任一不符 → AAD 不同 → GCM 校验失败
            decryptLtxEnvelope(envelope, session, "GET", "/api/items/query", 200)
            fail("expected AEADBadTagException")
        } catch (expected: Exception) {
            assertTrue(expected is java.security.GeneralSecurityException || expected is RuntimeException)
        }
    }

    // endregion

    // region AAD 构造

    @Test
    fun `aad matches json stringify of tuple`() {
        val aad = ltxResponseAad("SID1", "RID2", "get", "/api/items?q=1", 428, "identity")
        assertEquals(
            """["ltx-api-response-v1","SID1","RID2","GET","/api/items?q=1",428,"identity"]""",
            aad.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `aad escapes quotes and backslashes`() {
        val aad = ltxResponseAad("si\"d", "r\\id", "GET", "/p", 200, "identity")
        assertEquals(
            """["ltx-api-response-v1","si\"d","r\\id","GET","/p",200,"identity"]""",
            aad.toString(StandardCharsets.UTF_8),
        )
        assertNotNull(aad)
    }

    // endregion
}
