package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    // region compareVersionNames

    @Test
    fun compareVersionNames_remoteDateNewer() {
        assertEquals(1, compareVersionNames("v1.20261011", "1.20260811"))
    }

    @Test
    fun compareVersionNames_sameVersionRegardlessOfVPrefix() {
        assertEquals(0, compareVersionNames("v1.20261011", "1.20261011"))
        assertEquals(0, compareVersionNames("1.20261011", "V1.20261011"))
    }

    @Test
    fun compareVersionNames_remoteOlder() {
        assertEquals(-1, compareVersionNames("1.20260731", "v1.20260811"))
    }

    @Test
    fun compareVersionNames_longerVersionWinsWhenEqualSoFar() {
        assertEquals(1, compareVersionNames("1.20261011.1", "1.20261011"))
        assertEquals(-1, compareVersionNames("1.20261011", "1.20261011.1"))
    }

    @Test
    fun compareVersionNames_unparseableTreatedAsNoUpdate() {
        assertEquals(0, compareVersionNames("beta", "1.20261011"))
        assertEquals(0, compareVersionNames("1.x", "1.20261011"))
    }

    // endregion

    // region releaseNotesToPlainText

    @Test
    fun releaseNotesToPlainText_nullOrBlankIsEmpty() {
        assertEquals("", releaseNotesToPlainText(null))
        assertEquals("", releaseNotesToPlainText("   "))
    }

    @Test
    fun releaseNotesToPlainText_placeholderBodyIsEmpty() {
        assertEquals("", releaseNotesToPlainText("Release v1.20260811"))
        assertEquals("", releaseNotesToPlainText("release v1.20260811\n"))
    }

    @Test
    fun releaseNotesToPlainText_stripsMarkdownAndHeaderLine() {
        val body = """
            Release v1.20261011

            ## 本次更新

            ### 新增

            * **新功能**：支持 [更新链接](https://example.com)
            * 设置页新增检查更新入口

            ### 修复

            - 修复列表刷新问题
            ----
        """.trimIndent()

        val expected = """
            本次更新

            新增

            新功能：支持 更新链接
            设置页新增检查更新入口

            修复

            修复列表刷新问题
        """.trimIndent()

        assertEquals(expected, releaseNotesToPlainText(body))
    }

    // endregion

    // region selectReleaseApkAsset

    private fun asset(
        name: String,
        state: String = "uploaded",
        contentType: String = "application/vnd.android.package-archive",
        size: Long = 100L,
    ) = GithubAssetDto(id = 1L, name = name, state = state, contentType = contentType, size = size, browserDownloadUrl = "https://example.test/$name")

    @Test
    fun selectReleaseApkAsset_matchesReleaseApkNamingRule() {
        val assets = listOf(
            asset(name = "jbus_debug_v1.20261011.apk"),
            asset(name = "jbus_release_v1.20261011.apk", size = 200L),
            asset(name = "jbus_release_v1.20261011.sig", contentType = "application/octet-stream"),
            asset(name = "jbus_release_v1.20261011.pending.apk", state = "start"),
        )

        val selected = selectReleaseApkAsset(assets)

        assertEquals("jbus_release_v1.20261011.apk", selected?.name)
        assertEquals(200L, selected?.size)
    }

    @Test
    fun selectReleaseApkAsset_nullWhenNoValidAsset() {
        assertNull(selectReleaseApkAsset(emptyList()))
        assertNull(selectReleaseApkAsset(null))
        assertNull(
            selectReleaseApkAsset(
                listOf(asset(name = "jbus_release_v1.20261011.apk", contentType = "application/zip"))
            )
        )
    }

    // endregion

    // region release payload parsing

    @Test
    fun parseGitHubReleasePayload() {
        val payload = """
            {
              "tag_name": "v1.20260811",
              "name": "v1.20260811",
              "body": "Release v1.20260811",
              "assets": [
                {
                  "id": 12345,
                  "name": "jbus_release_v1.20260811.apk",
                  "state": "uploaded",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 5656883,
                  "browser_download_url": "https://github.com/Mupceet/JBusDriverNext/releases/download/v1.20260811/jbus_release_v1.20260811.apk"
                }
              ]
            }
        """.trimIndent()

        val release = GSON.fromJson<GithubReleaseDto>(payload)

        assertEquals("v1.20260811", release?.tagName)
        assertEquals(1, release?.assets?.size)
        val asset = release?.assets?.first()
        assertEquals("jbus_release_v1.20260811.apk", asset?.name)
        assertEquals("application/vnd.android.package-archive", asset?.contentType)
        assertEquals(5656883L, asset?.size)
        assertTrue(asset?.browserDownloadUrl.orEmpty().endsWith(".apk"))
    }

    // endregion
}
