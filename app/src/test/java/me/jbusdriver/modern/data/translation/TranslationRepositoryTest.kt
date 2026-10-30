package me.jbusdriver.modern.data.translation

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRepositoryTest {

    private class FakeEngine(var result: Result<String>) : TranslatorEngine {
        var calls = 0
        override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
            calls++
            return result
        }
    }

    private class FakeSettings(
        var config: TranslatorConfig = TranslatorConfig(TranslationProvider.GOOGLE, "", ""),
    ) : TranslationSettingsReader {
        override val translateByDefault = MutableStateFlow(false)
        override suspend fun currentTranslatorConfig(): TranslatorConfig = config
    }

    private class FakeCacheStore : CacheStore {
        val memory = mutableMapOf<String, String>()
        val disk = mutableMapOf<String, String>()
        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) { memory[key] = value }
        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) { disk[key] = value }
    }

    private fun repo(
        cache: FakeCacheStore = FakeCacheStore(),
        engine: FakeEngine = FakeEngine(Result.success("譯文")),
        settings: FakeSettings = FakeSettings(),
    ) = DefaultTranslationRepository(
        cacheStore = cache,
        settings = settings,
        engineFactory = { _ -> engine },
    )

    @Test
    fun translate_successWritesMemoryAndDiskCache() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.success("譯文"))
        val result = repo(cache, engine).translate("テスト")
        assertEquals("譯文", result.getOrThrow())
        assertEquals(1, engine.calls)
        assertEquals(1, cache.memory.size)
        assertEquals(1, cache.disk.size)
    }

    @Test
    fun translate_memoryCacheHitSkipsEngine() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.success("譯文"))
        val repository = repo(cache, engine)
        repository.translate("テスト")
        val second = repository.translate("テスト")
        assertEquals("譯文", second.getOrThrow())
        assertEquals(1, engine.calls)
    }

    @Test
    fun translate_diskCacheHitBackfillsMemory() = runTest {
        val cache = FakeCacheStore()
        cache.disk["trans:google:zh-TW:${sha256("テスト")}"] = "磁盤譯文"
        val engine = FakeEngine(Result.success("不應請求"))
        val result = repo(cache, engine).translate("テスト")
        assertEquals("磁盤譯文", result.getOrThrow())
        assertEquals(0, engine.calls)
        assertEquals("磁盤譯文", cache.memory.values.single())
    }

    @Test
    fun translate_failureIsNotCached() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.failure(RuntimeException("boom")))
        val repository = repo(cache, engine)
        assertTrue(repository.translate("テスト").isFailure)
        assertTrue(repository.translate("テスト").isFailure)
        assertEquals(2, engine.calls)
        assertTrue(cache.memory.isEmpty())
        assertTrue(cache.disk.isEmpty())
    }

    @Test
    fun translate_customProviderWithoutTemplateFailsFastWithoutEngineCall() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.success("x"))
        val result = repo(cache, engine, FakeSettings(TranslatorConfig(TranslationProvider.CUSTOM, "", "")))
            .translate("テスト")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TranslationConfigException)
        assertEquals(0, engine.calls)
    }

    @Test
    fun translate_concurrentSameKeyCallsEngineOnce() = runTest {
        val engine = FakeEngine(Result.success("譯文"))
        val repository = repo(FakeCacheStore(), engine)
        val results = (1..3).map { async { repository.translate("テスト") } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, engine.calls)
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
