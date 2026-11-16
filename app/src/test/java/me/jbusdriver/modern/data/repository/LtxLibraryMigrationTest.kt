package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.ltx.LtxMigratedException
import me.jbusdriver.modern.data.ltx.LtxProtocolException
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * [fetchLtxLibraryWithMigration] 迁移自愈编排：旧域 410 公告采纳新址写回偏好
 * 并重试一次；其余异常原样传播。fixture 为合成地址。
 */
class LtxLibraryMigrationTest {

    private class RecordingSettings : LtxSettingsSource {
        override val ltxEnabled: StateFlow<Boolean> = MutableStateFlow(true)
        val saved = mutableListOf<String>()
        override suspend fun currentLtxBaseUrl(): String = "https://old.example.test"
        override suspend fun setLtxBaseUrl(baseUrl: String) {
            saved.add(baseUrl)
        }
    }

    @Test
    fun `first fetch success does not touch settings`() = runTest {
        val settings = RecordingSettings()
        val calls = mutableListOf<String>()
        val json = fetchLtxLibraryWithMigration("https://old.example.test", settings) {
            calls.add(it); """{"items":[]}"""
        }
        assertEquals("""{"items":[]}""", json)
        assertEquals(listOf("https://old.example.test"), calls)
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun `migration announcement adopts target url and retries once`() = runTest {
        val settings = RecordingSettings()
        val calls = mutableListOf<String>()
        val json = fetchLtxLibraryWithMigration("https://old.example.test", settings) { base ->
            calls.add(base)
            if (base == "https://old.example.test") throw LtxMigratedException("https://new.example.test")
            """{"items":[],"migrated_from":"$base"}"""
        }
        assertEquals("""{"items":[],"migrated_from":"https://new.example.test"}""", json)
        assertEquals(listOf("https://old.example.test", "https://new.example.test"), calls)
        assertEquals(listOf("https://new.example.test"), settings.saved)
    }

    @Test
    fun `migration to the same url degrades to failure without retry`() = runTest {
        val settings = RecordingSettings()
        val calls = mutableListOf<String>()
        try {
            fetchLtxLibraryWithMigration("https://old.example.test", settings) {
                calls.add(it)
                throw LtxMigratedException("https://old.example.test")
            }
            fail("expected IOException")
        } catch (e: IOException) {
            // 公告新址与当前地址相同：服务端公告异常，退化为普通失败
        }
        assertEquals(listOf("https://old.example.test"), calls)
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun `non-migration exceptions propagate untouched`() = runTest {
        val settings = RecordingSettings()
        val calls = mutableListOf<String>()
        try {
            fetchLtxLibraryWithMigration("https://old.example.test", settings) {
                calls.add(it)
                throw LtxProtocolException("https://old.example.test/api/items")
            }
            fail("expected LtxProtocolException")
        } catch (e: LtxProtocolException) {
            // 原样传播，不写偏好、不重试
        }
        assertEquals(1, calls.size)
        assertTrue(settings.saved.isEmpty())
    }
}
