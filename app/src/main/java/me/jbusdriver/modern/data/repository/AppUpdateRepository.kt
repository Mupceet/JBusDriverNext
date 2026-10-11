package me.jbusdriver.modern.data.repository

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.fromJson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 应用内更新信息（数据已整理为可直接展示的形态）。 */
data class AppUpdateInfo(
    /** 远端版本名，如 `1.20261011`（已去掉 tag 的 `v` 前缀）。 */
    val versionName: String,
    /** 更新说明纯文本；无有效内容时为空字符串，UI 应隐藏说明区块。 */
    val releaseNotes: String,
    /** APK 下载地址（优先 gh-proxy 加速，探测不可达时为直连地址）。 */
    val downloadUrl: String,
    /** APK 文件名，如 `jbus_release_v1.20261011.apk`。 */
    val fileName: String,
    /** APK 大小（字节）；未知时为 0。 */
    val fileSizeBytes: Long,
)

/**
 * 应用更新检查仓库。
 *
 * 数据源为 GitHub Releases（`releases/latest`）。Release 正文由发布流程
 * （`.github/workflows/release.yml`）从 `app/src/main/assets/updateLog.md`
 * 组装，这里只负责拉取、筛选 APK 资产与版本比较。
 */
interface AppUpdateRepository {
    /**
     * 检查更新。
     *
     * @return 有新版本时返回 [AppUpdateInfo]；已是最新版本返回 `null`。
     * @throws IOException 网络失败或远端 Release 缺少可用的 APK 资产。
     */
    suspend fun checkForUpdate(): AppUpdateInfo?
}

@Singleton
class DefaultAppUpdateRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppUpdateRepository {

    override suspend fun checkForUpdate(): AppUpdateInfo? = withContext(ioDispatcher) {
        val release = fetchLatestRelease()
        val asset = selectReleaseApkAsset(release.assets)
            ?: throw IOException("No release APK asset in ${release.tagName}")
        val tagName = requireNotNull(release.tagName) { "Release without tag_name" }
        if (compareVersionNames(tagName, BuildConfig.VERSION_NAME) <= 0) {
            return@withContext null
        }
        val downloadUrl = requireNotNull(asset.browserDownloadUrl) {
            "Release asset without browser_download_url"
        }
        AppUpdateInfo(
            versionName = tagName.removePrefix("v").removePrefix("V"),
            releaseNotes = releaseNotesToPlainText(release.body),
            downloadUrl = resolveDownloadUrl(downloadUrl),
            fileName = asset.name,
            fileSizeBytes = asset.size ?: 0L,
        )
    }

    // region network

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun fetchLatestRelease(): GithubReleaseDto {
        // 直连失败时经 gh-proxy 重试，覆盖 GitHub 直连不可达的网络环境
        val endpoints = listOf(LATEST_RELEASE_URL, GH_PROXY_PREFIX + LATEST_RELEASE_URL)
        var lastError: IOException? = null
        for (url in endpoints) {
            try {
                val json = executeJsonRequest(url)
                return GSON.fromJson<GithubReleaseDto>(json)
                    ?: throw IOException("Malformed release payload from $url")
            } catch (e: IOException) {
                lastError = e
                KLog.t(TAG).e("Update check failed for $url", e)
            }
        }
        throw lastError ?: IOException("Update check failed")
    }

    private fun executeJsonRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "JBusDriver/${BuildConfig.VERSION_NAME}")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url")
            }
            return response.body?.string() ?: throw IOException("Empty body from $url")
        }
    }

    /** 探测 gh-proxy 加速地址是否可达（Range 探测），不可达则回退直连。 */
    private fun resolveDownloadUrl(directUrl: String): String {
        val proxiedUrl = GH_PROXY_PREFIX + directUrl
        return try {
            val probe = Request.Builder()
                .url(proxiedUrl)
                .head()
                .header("Range", "bytes=0-0")
                .build()
            httpClient.newCall(probe).execute().use { response ->
                if (response.isSuccessful) proxiedUrl else directUrl
            }
        } catch (_: IOException) {
            KLog.t(TAG).w("Proxy probe failed, falling back to direct URL")
            directUrl
        }
    }

    // endregion

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Mupceet/JBusDriverNext/releases/latest"
        private const val GH_PROXY_PREFIX = "https://gh-proxy.com/"
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        private const val READ_TIMEOUT_SECONDS = 10L
    }
}

// region GitHub Releases API payload

@Keep
data class GithubReleaseDto(
    @SerializedName("tag_name") val tagName: String?,
    val name: String?,
    val body: String?,
    val assets: List<GithubAssetDto>?,
)

@Keep
data class GithubAssetDto(
    val id: Long,
    val name: String,
    val state: String?,
    @SerializedName("content_type") val contentType: String?,
    val size: Long?,
    @SerializedName("browser_download_url") val browserDownloadUrl: String?,
)

// endregion

// region pure helpers (unit tested)

/** 版本名比较：按 `.` 分段数字比较（本项目为 `1.yyyyMMdd` 日期版本）。 */
internal fun compareVersionNames(remote: String, current: String): Int {
    fun segments(value: String): List<Long>? = value.trim()
        .removePrefix("v").removePrefix("V")
        .split('.')
        .map { it.toLongOrNull() }
        .takeIf { parts -> parts.all { it != null } }
        ?.map { requireNotNull(it) }

    val remoteParts = segments(remote) ?: return 0
    val currentParts = segments(current) ?: return 0
    val length = maxOf(remoteParts.size, currentParts.size)
    for (index in 0 until length) {
        val remoteValue = remoteParts.getOrElse(index) { 0L }
        val currentValue = currentParts.getOrElse(index) { 0L }
        if (remoteValue != currentValue) {
            return if (remoteValue > currentValue) 1 else -1
        }
    }
    return 0
}

/**
 * Release 正文 → 更新弹框纯文本。
 *
 * 不引入 Markdown 渲染，只做最小语法剥离：发布流程注入的 `Release vX`
 * 标题行（版本号已在弹框头部展示）、标题 `#` 前缀、粗体 `**`、链接
 * `[文字](地址)` → 文字、列表行 `* `-`- `-`+ ` 前缀、`----` 分隔线 →
 * 空行；连续空行压成单个、首尾空白裁掉。占位正文（仅 `Release vX`
 * 一行）剥离后为空字符串。
 */
internal fun releaseNotesToPlainText(body: String?): String {
    if (body.isNullOrBlank()) return ""
    return body.lines()
        .filterNot { it.trim().matches(releaseHeaderLine) }
        .map(::plainNoteLine)
        .map(String::trim)
        .let(::squeezeBlankNoteLines)
}

/** 发布流程注入的固定标题行，如 `Release v1.20261011`。 */
private val releaseHeaderLine = Regex("(?i)^release\\s+v[\\w.+-]*$")

private val noteHeading = Regex("^#{1,6}\\s+")
private val noteBullet = Regex("^[*_+\\-]\\s+")
private val noteDivider = Regex("^[-*_]{3,}$")
private val noteBold = Regex("\\*\\*(.+?)\\*\\*")
private val noteLink = Regex("\\[([^]]+)]\\([^)]*\\)")

private fun plainNoteLine(line: String): String {
    var text = line.trim()
    if (noteDivider.matches(text)) return ""
    text = text.replaceFirst(noteHeading, "")
    text = text.replaceFirst(noteBullet, "")
    text = noteBold.replace(text, "$1")
    text = noteLink.replace(text, "$1")
    return text
}

private fun squeezeBlankNoteLines(lines: List<String>): String {
    val result = mutableListOf<String>()
    var lastBlank = true // 首部空行一并压掉
    for (line in lines) {
        val blank = line.isEmpty()
        if (blank && lastBlank) continue
        result.add(line)
        lastBlank = blank
    }
    return result.joinToString("\n").trimEnd()
}

/** 发布资产名规则：`jbus_release_v<versionName>.apk`（与本地构建输出一致）。 */
private val releaseApkNamePattern = Regex("^jbus_release_v.+\\.apk$")

internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** 从 Release 资产中选出可用的正式包 APK（包名规则不变）。 */
internal fun selectReleaseApkAsset(assets: List<GithubAssetDto>?): GithubAssetDto? =
    assets.orEmpty()
        .filter { it.state == "uploaded" }
        .filter { it.contentType.equals(APK_MIME_TYPE, ignoreCase = true) }
        .filter { releaseApkNamePattern.matches(it.name) }
        .maxByOrNull { it.size ?: 0L }

// endregion
