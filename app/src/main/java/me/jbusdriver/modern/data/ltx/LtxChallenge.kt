package me.jbusdriver.modern.data.ltx

import java.io.IOException

/**
 * 判断 LTX 响应体是否为"需要人机验证"的拦截响应。
 *
 * 两种形态（2026-11 实测）：
 * - Worker 验证门：JSON `{"error":"请先完成人机验证","reason":"verify","sitekey":...}`
 *   或 text/plain "Automated clients are not allowed. 请用浏览器访问。"
 * - 云端挑战页：HTML（Just a moment / challenge-platform 等 Cloudflare 特征）
 *
 * 注意区分两类**非**人机验证的新拦截形态（2026-11-15 起迁移站）：
 * - 加密会话缺失：428 JSON `{"reason":"cryptoSession"}` → [isLtxCryptoSessionRequired]
 * - 站点迁移公告：410 JSON `{"reason":"migrated"}` → [parseLtxMigratedBaseUrl]
 *
 * 正常载荷是单包 JSON（`{` / `[` 开头），JSON 分支只在出现验证门特征字段时
 * 判真，避免链接文本误判——与主站 `isDriverVerifyPage` 同思路的纯函数，
 * 便于单元测试，[DefaultLtxRepository] 的重试策略依赖该判定。
 */
internal fun isLtxCloudflareChallenge(body: String): Boolean {
    val trimmed = body.trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    val isJson = trimmed.startsWith("{") || trimmed.startsWith("[")
    if (isJson) {
        return trimmed.contains("\"sitekey\"") ||
                (trimmed.contains("\"reason\"") && trimmed.contains("verify"))
    }
    return LTX_CHALLENGE_MARKERS.any { trimmed.contains(it, ignoreCase = true) }
}

private val LTX_CHALLENGE_MARKERS = listOf(
    // Worker 机器人拦截（text/plain）
    "automated clients",
    "请用浏览器访问",
    // JS/托管挑战等待页标题（"Just a moment..."）
    "just a moment",
    // 挑战脚本与令牌：/cdn-cgi/challenge-platform/、cf-chl-* 与 JS 侧 _cf_chl_opt/__cf_chl_rt_tk
    "challenge-platform",
    "cf-chl",
    "cf_chl",
    // 旧版浏览器校验页
    "cf-browser-verification",
    "checking your browser",
    // 拦截页（"Attention Required! | Cloudflare"）
    "attention required",
    // Cloudflare Access 登录页（重定向跟随后的最终页）
    "cloudflareaccess",
)

/** LTX 拉取命中 Cloudflare 挑战；继承 IOException 以复用现有失败降级路径。 */
internal class LtxChallengeException(val url: String) :
    IOException("Cloudflare challenge at $url")

/**
 * 判断 LTX 响应体是否为「加密会话未建立」拦截（新站 2026-11 起，
 * 428 Precondition Required + JSON `{"reason":"cryptoSession"}`）。
 *
 * 新站要求先 `POST /api/crypto/session` 完成 ECDH+HKDF 握手派生 AES-GCM
 * 密钥，随后请求携带会话头；纯 OkHttp 与页面注入 XHR 均无法满足，与
 * 人机验证（Cookie 可解决）是两类问题。
 */
internal fun isLtxCryptoSessionRequired(body: String): Boolean {
    val trimmed = body.trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    if (!trimmed.startsWith("{")) return false
    return trimmed.contains("\"reason\"") && trimmed.contains("\"cryptoSession\"")
}

/** 新站加密会话协议当前客户端无法满足；继承 IOException 复用失败降级路径。 */
internal class LtxProtocolException(val url: String) :
    IOException("LTX crypto session required at $url")

/** 旧域返回 410 迁移公告；[targetUrl] 为公告给出的新站点地址。 */
internal class LtxMigratedException(val targetUrl: String) :
    IOException("LTX site migrated to $targetUrl")
