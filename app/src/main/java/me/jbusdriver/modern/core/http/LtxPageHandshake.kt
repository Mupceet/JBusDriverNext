package me.jbusdriver.modern.core.http

import android.webkit.WebView
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.WebViewHelper.currentWebSession
import me.jbusdriver.modern.core.http.WebViewHelper.evaluateJs
import org.json.JSONObject

private const val TAG = "LtxWebGate"

/**
 * LTX 页内加密会话握手（共享工具）。
 *
 * 供两处复用：隐形网关（[DefaultLtxWebViewGateway]，自动通道自愈）与验证浏览器
 * （RouteLtxVerify 接线的 postGate 钩子，导出会话前先完成握手）。在已过门禁的
 * LTX 页面上下文注入 WebCrypto 脚本，完成 ECDH P-256 + HKDF-SHA256 会话建立
 * （与站点 SPA 同协议），把 sid/密钥/过期时间交还宿主。
 *
 * [pump] 幂等可重入：重复调用先补注入（脚本内以 `window.__ltxHsDone` 防重），
 * 再收割结果；显式失败（含 4xx）复位标记供下轮重试——页内 SPA 可能在握手期间
 * 后台补完 Turnstile 验证，**同页重试是自愈的必要条件**（2026-11-25 真机教训：
 * 提前放弃会陷入验证浏览器闪现循环）。
 */
object LtxPageHandshake {

    /** 页内握手产物：sid/AES 密钥/过期时间；Cookie/UA 由调用方按目标 URL 导出。 */
    data class Result(
        val sid: String,
        val aesKeyBase64Url: String,
        val expiresAtMillis: Long,
    )

    /**
     * 推进一轮握手：补注入 → 收割 → 失败复位。成功返回 [Result]；
     * 未完成（未启动/进行中）或本轮失败返回 null（已复位，下轮调用重新注入）。
     */
    suspend fun pump(webView: WebView): Result? {
        runCatching { webView.evaluateJs(HANDSHAKE_START_JS) }
        val result = runCatching { webView.evaluateJs(HANDSHAKE_READ_JS) }.getOrNull()
        val parsed = parseHandshakeResult(result)
        if (parsed != null) return parsed
        if (result != null && result != "null") {
            runCatching { webView.evaluateJs(HANDSHAKE_RESET_JS) }
        }
        return null
    }

    /**
     * 推进握手并导出目标 [url] 的 Cookie/UA，两者齐备返回完整 [LtxWebViewSession]
     * （验证浏览器导出前调用：探测门禁通过只证明边缘放行，须握手完成才算完全验证）。
     */
    suspend fun establish(webView: WebView, url: String): LtxWebViewSession? {
        val handshake = pump(webView) ?: return null
        val webSession = webView.currentWebSession(url) ?: return null
        return LtxWebViewSession(
            sid = handshake.sid,
            aesKeyBase64Url = handshake.aesKeyBase64Url,
            expiresAtMillis = handshake.expiresAtMillis,
            webSession = webSession,
        )
    }

    /** 解析握手脚本写回的结果 JSON：ok 时返回结果；显式失败打日志返回 null。 */
    private fun parseHandshakeResult(result: String?): Result? {
        if (result == null || result == "null") return null
        val json = runCatching {
            JSONObject(WebViewHelper.unescapeJsString(result))
        }.getOrNull() ?: return null
        if (!json.optBoolean("ok")) {
            if (json.has("error") || json.has("status")) {
                KLog.t(TAG).w("LTX page handshake failed: ${json.optString("error")} status=${json.optInt("status")}")
            }
            return null
        }
        val sid = json.optString("sid")
        val key = json.optString("key")
        val expiresAt = json.optLong("expiresAt")
        if (sid.isNotEmpty() && key.isNotEmpty() && expiresAt > 0) {
            return Result(sid = sid, aesKeyBase64Url = key, expiresAtMillis = expiresAt)
        }
        return null
    }

    /**
     * 页内握手启动脚本：门禁通过后注入，在页面上下文以 WebCrypto 异步完成
     * ECDH P-256 + HKDF-SHA256 会话建立（与站点 SPA 同协议），结果写入
     * window.__ltxHsResult。replaceAll 均为字符串字面量替换（无正则）。
     */
    private const val HANDSHAKE_START_JS = """
(function() {
  if (window.__ltxHsDone) return 'already';
  window.__ltxHsDone = true;
  window.__ltxHsResult = null;
  function b64(buf) {
    var s = '';
    for (var i = 0; i < buf.length; i++) s += String.fromCharCode(buf[i]);
    return btoa(s).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
  }
  function unb64(t) {
    t = t.replaceAll('-', '+').replaceAll('_', '/');
    while (t.length % 4 !== 0) t += '=';
    var bin = atob(t);
    var out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  (async function() {
    try {
      var kp = await crypto.subtle.generateKey({name: 'ECDH', namedCurve: 'P-256'}, false, ['deriveBits']);
      var raw = new Uint8Array(await crypto.subtle.exportKey('raw', kp.publicKey));
      var resp = await fetch('/api/crypto/session', {
        method: 'POST',
        credentials: 'include',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({v: 1, publicKey: b64(raw)})
      });
      if (!resp.ok) { window.__ltxHsResult = JSON.stringify({ok: false, status: resp.status}); return; }
      var s = await resp.json();
      if (s.v !== 1 || !s.sid) { window.__ltxHsResult = JSON.stringify({ok: false, error: 'bad session response'}); return; }
      var serverKey = await crypto.subtle.importKey('raw', unb64(s.publicKey), {name: 'ECDH', namedCurve: 'P-256'}, false, []);
      var shared = new Uint8Array(await crypto.subtle.deriveBits({name: 'ECDH', public: serverKey}, kp.privateKey, 256));
      var hk = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveBits']);
      var info = new TextEncoder().encode('ltx-api-response-v1:' + s.sid);
      var keyBits = new Uint8Array(await crypto.subtle.deriveBits({name: 'HKDF', hash: 'SHA-256', salt: unb64(s.salt), info: info}, hk, 256));
      window.__ltxHsResult = JSON.stringify({ok: true, sid: s.sid, key: b64(keyBits), expiresAt: s.expiresAt});
    } catch (e) {
      window.__ltxHsResult = JSON.stringify({ok: false, error: String(e)});
    }
  })();
  return 'started';
})()
"""

    /** 读取握手脚本写入 window 变量的结果 JSON（未完成时为 null）。 */
    private const val HANDSHAKE_READ_JS = """
(function() {
  return window.__ltxHsResult === undefined ? null : window.__ltxHsResult;
})()
"""

    /** 复位握手标记：握手失败后允许下一轮探测重新注入。 */
    private const val HANDSHAKE_RESET_JS = """
(function() {
  window.__ltxHsDone = false;
  window.__ltxHsResult = null;
  return 'reset';
})()
"""
}
