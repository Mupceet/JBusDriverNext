package me.jbusdriver.modern.data.ltx

import androidx.annotation.Keep
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * LTX 迁移站（ltxlib.dpdns.org，2026-11-15 起）加密会话协议的原生实现，
 * 与站点 SPA 混淆 JS（zs/Rs/Cs/ws）逐位对齐：
 *
 * 1. `POST /api/crypto/session` body `{"v":1,"publicKey":<base64url(raw65)>}`
 *    → `{v, sid, publicKey, salt, expiresAt}`；
 * 2. ECDH P-256(client, server) → 32 字节共享密钥（WebCrypto deriveBits 定长，
 *    JCE 需左补齐）→ HKDF-SHA256(salt, info="ltx-api-response-v1:"+sid) → AES-256 key；
 * 3. 数据请求只加头：`X-LTX-Session: sid`、`X-LTX-Request: base64url(16字节随机)`
 *    （请求体明文）；响应 428 cryptoSession 时重建会话重试一次；
 * 4. 响应信封 `{v:1, sid, rid, iv, body, encoding}`：AES-GCM 解密，
 *    AAD=`JSON.stringify(["ltx-api-response-v1",sid,rid,METHOD,path+search,status,encoding])`，
 *    encoding=gzip 时解压得到明文 JSON。
 *
 * 全部为无 Android 依赖的纯函数，便于单元测试（HKDF 用 RFC 5869 向量、
 * 加解密用同构造回环验证）。
 */

private const val HASH_LEN = 32
private const val EC_COORD_LEN = 32
private const val EC_RAW_POINT_LEN = 65
private const val GCM_IV_LEN = 12
internal const val LTX_AAD_PREFIX = "ltx-api-response-v1"

/** 会话协商响应（`POST /api/crypto/session`，v1）。 */
@Keep
data class LtxSessionHandshakeDto(
    val v: Int? = null,
    val sid: String? = null,
    val publicKey: String? = null,
    val salt: String? = null,
    val expiresAt: Long? = null,
)

/** 会话协商请求体（v1）。 */
@Keep
data class LtxSessionRequestBodyDto(val v: Int = 1, val publicKey: String)

/** 加密响应信封（v1）。 */
@Keep
data class LtxEncryptedEnvelopeDto(
    val v: Int? = null,
    val sid: String? = null,
    val rid: String? = null,
    val iv: String? = null,
    val body: String? = null,
    val encoding: String? = null,
) {
    /** 是否形如加密信封（字段齐备）；否则按明文载荷处理（协议回退容错）。 */
    fun looksLikeEnvelope(): Boolean =
        v != null && !sid.isNullOrEmpty() && !rid.isNullOrEmpty() &&
                !iv.isNullOrEmpty() && !body.isNullOrEmpty()
}

/** 已建立的加密会话；密钥仅存进程内存，不落盘。 */
class LtxCryptoSession(
    val sid: String,
    val aesKey: SecretKey,
    val expiresAtMillis: Long,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis in 1 until nowMillis
}

// region 编码

internal fun base64UrlEncode(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** base64url 无填充解码（字符集与长度校验与站点 As() 一致）；非法返回 null。 */
internal fun base64UrlDecode(text: String): ByteArray? {
    if (text.isEmpty() || text.length % 4 == 1 || !text.matches(Regex("[A-Za-z0-9_-]+"))) return null
    return try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
}

// endregion

// region HKDF（RFC 5869）

internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
    require(outputLength > 0) { "outputLength must be positive" }
    val mac = Mac.getInstance("HmacSHA256")
    // extract：空 salt 按 RFC 以 32 字节零填充
    mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(HASH_LEN) else salt, "HmacSHA256"))
    val prk = mac.doFinal(ikm)
    // expand
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val okm = ByteArray(outputLength)
    var t = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < outputLength) {
        mac.reset()
        t = mac.doFinal(t + info + byteArrayOf(counter.toByte()))
        val take = minOf(HASH_LEN, outputLength - offset)
        System.arraycopy(t, 0, okm, offset, take)
        offset += take
        counter++
    }
    return okm
}

/** HKDF info：`ltx-api-response-v1:<sid>`（与站点 SPA 的 ws() 一致）。 */
internal fun ltxHkdfInfo(sid: String): ByteArray = "$LTX_AAD_PREFIX:$sid".toByteArray(StandardCharsets.UTF_8)

// endregion

// region 密钥

/** 客户端密钥对（secp256r1，即 WebCrypto 的 P-256）。 */
internal fun newLtxClientKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
    initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
}.genKeyPair()

/** 公钥导出 raw 未压缩点（04||X||Y，65 字节）的 base64url（请求体格式）。 */
internal fun ltxPublicKeyRawBase64Url(keyPair: KeyPair): String {
    val w = (keyPair.public as ECPublicKey).w
    val raw = ByteArray(EC_RAW_POINT_LEN)
    raw[0] = 0x04
    copyCoordToFixed(w.affineX, raw, 1)
    copyCoordToFixed(w.affineY, raw, EC_COORD_LEN + 1)
    return base64UrlEncode(raw)
}

private fun copyCoordToFixed(value: BigInteger, out: ByteArray, offset: Int) {
    val bytes = value.toByteArray()
    val len = EC_COORD_LEN
    when {
        bytes.size == len -> System.arraycopy(bytes, 0, out, offset, len)
        bytes.size > len -> System.arraycopy(bytes, bytes.size - len, out, offset, len)
        else -> System.arraycopy(bytes, 0, out, offset + len - bytes.size, bytes.size)
    }
}

/** 服务端 65 字节未压缩公钥 → PublicKey（曲线沿用客户端参数，均为 P-256）。 */
internal fun importLtxServerPublicKey(raw: ByteArray, params: ECParameterSpec): PublicKey {
    require(raw.size == EC_RAW_POINT_LEN && raw[0].toInt() == 4) { "bad server public key point" }
    val x = BigInteger(1, raw.copyOfRange(1, EC_COORD_LEN + 1))
    val y = BigInteger(1, raw.copyOfRange(EC_COORD_LEN + 1, EC_RAW_POINT_LEN))
    return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
}

/**
 * 完成会话建立：ECDH(client, server) → HKDF-SHA256(salt, info=ws(sid)) → AES-256 key。
 * 共享密钥左补齐 32 字节以对齐 WebCrypto deriveBits 的定长输出。
 */
internal fun establishLtxSession(
    clientKeyPair: KeyPair,
    handshake: LtxSessionHandshakeDto,
): LtxCryptoSession {
    require(handshake.v == 1) { "unsupported session version ${handshake.v}" }
    val sid = handshake.sid.takeIf { !it.isNullOrBlank() } ?: error("session sid missing")
    val expiresAt = handshake.expiresAt ?: 0L
    require(expiresAt > System.currentTimeMillis()) { "session already expired" }
    val serverPubRaw = base64UrlDecode(handshake.publicKey.orEmpty())
        ?.also { require(it.size == EC_RAW_POINT_LEN) { "bad server publicKey length" } }
        ?: error("bad server publicKey encoding")
    val salt = base64UrlDecode(handshake.salt.orEmpty())
        ?.also { require(it.size == HASH_LEN) { "bad salt length" } }
        ?: error("bad salt encoding")
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(clientKeyPair.private)
    agreement.doPhase(importLtxServerPublicKey(serverPubRaw, (clientKeyPair.public as ECPublicKey).params), true)
    val secret = agreement.generateSecret()
    val shared = ByteArray(HASH_LEN)
    System.arraycopy(secret, 0, shared, HASH_LEN - secret.size, secret.size)
    val keyBytes = hkdfSha256(shared, salt, ltxHkdfInfo(sid), HASH_LEN)
    return LtxCryptoSession(sid, SecretKeySpec(keyBytes, "AES"), expiresAt)
}

/** 新请求 id：base64url(16 字节安全随机)，与站点 SPA 每请求新生成一致。 */
internal fun newLtxRequestId(random: SecureRandom = SecureRandom()): String =
    base64UrlEncode(ByteArray(16).also(random::nextBytes))

// endregion

// region 响应解密

/**
 * 响应 AAD：与站点 SPA Cs() 的 JSON.stringify 输出逐字节一致
 * `["ltx-api-response-v1",sid,rid,METHOD,path+search,status,encoding]`。
 * method 大写；status 为数字；紧凑无空格序列化。
 */
internal fun ltxResponseAad(
    sid: String,
    rid: String,
    method: String,
    pathAndSearch: String,
    httpStatus: Int,
    encoding: String,
): ByteArray = buildString {
    append('[')
    append(jsonString(LTX_AAD_PREFIX)).append(',')
    append(jsonString(sid)).append(',')
    append(jsonString(rid)).append(',')
    append(jsonString(method.uppercase())).append(',')
    append(jsonString(pathAndSearch)).append(',')
    append(httpStatus).append(',')
    append(jsonString(encoding))
    append(']')
}.toByteArray(StandardCharsets.UTF_8)

/** JSON 字符串字面量（JSON.stringify 语义：仅转义必须转义的字符，不做 HTML/Unicode 转义。 */
/** 输入域为 base64url/固定 token（sid/rid/method/path/encoding），不含控制字符， */
/** 控制字符统一走 \u00XX 兜底（理论不可达）。 */
private fun jsonString(value: String): String = buildString {
    append('"')
    for (c in value) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\b' -> append("\\b")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u").append(String.format("%04x", c.code))
            else -> append(c)
        }
    }
    append('"')
}

/**
 * 解密 v1 加密信封为明文 JSON 文本。[method]/[pathAndSearch]/[httpStatus] 必须与
 * 实际请求/响应一致（进入 AAD，不匹配即 GCM 校验失败抛 AEADBadTagException）。
 */
internal fun decryptLtxEnvelope(
    envelope: LtxEncryptedEnvelopeDto,
    session: LtxCryptoSession,
    method: String,
    pathAndSearch: String,
    httpStatus: Int,
): String {
    require(envelope.v == 1 && envelope.sid == session.sid) { "envelope sid mismatch" }
    val iv = base64UrlDecode(envelope.iv.orEmpty()) ?: error("bad envelope iv encoding")
    require(iv.size == GCM_IV_LEN) { "envelope iv length ${iv.size} != $GCM_IV_LEN" }
    val cipherText = base64UrlDecode(envelope.body.orEmpty()) ?: error("bad envelope body encoding")
    val encoding = envelope.encoding ?: "identity"
    require(encoding == "identity" || encoding == "gzip") { "unknown envelope encoding $encoding" }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, session.aesKey, GCMParameterSpec(128, iv))
    cipher.updateAAD(ltxResponseAad(session.sid, envelope.rid.orEmpty(), method, pathAndSearch, httpStatus, encoding))
    // WebCrypto 密文 = 明文 || 16 字节 tag，与 JCE GCM doFinal 期望布局一致
    val plain = cipher.doFinal(cipherText)
    val bytes = if (encoding == "gzip") gunzip(plain) else plain
    return String(bytes, StandardCharsets.UTF_8)
}

private fun gunzip(data: ByteArray): ByteArray =
    GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

// endregion
