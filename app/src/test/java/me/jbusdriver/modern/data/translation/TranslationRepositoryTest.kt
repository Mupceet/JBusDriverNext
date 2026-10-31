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

    private class FakeEngine(var result: Result<String> = Result.success("譯文")) : TranslatorEngine {
        var calls = 0
        var lastTarget: String? = null
        override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
            calls++
            lastTarget = targetLang
            return result
        }
    }

    private class FakeSettings(var config: TranslatorConfig) : TranslationSettingsReader {
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
        engine: FakeEngine = FakeEngine(),
        settings: FakeSettings = FakeSettings(TranslatorConfig(TranslationProvider.GOOGLE, "zh-TW")),
    ) = DefaultTranslationRepository(cache, settings, TranslatorEngineFactory { _ -> engine }) to settings

    @Test
    fun translate_successWritesMemoryAndDiskCache() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine()
        val (repository, _) = repo(cache, engine)
        assertEquals("譯文", repository.translate("テスト").getOrThrow())
        assertEquals(1, engine.calls)
        assertEquals(1, cache.memory.size)
        assertEquals(1, cache.disk.size)
    }

    @Test
    fun translate_memoryCacheHitSkipsEngine() = runTest {
        val engine = FakeEngine()
        val (repository, _) = repo(engine = engine)
        repository.translate("テスト")
        assertEquals("譯文", repository.translate("テスト").getOrThrow())
        assertEquals(1, engine.calls)
    }

    @Test
    fun translate_diskCacheHitBackfillsMemory() = runTest {
        val cache = FakeCacheStore()
        cache.disk[key("google", "auto", "zh-TW", "テスト")] = "磁盤譯文"
        val engine = FakeEngine()
        val (repository, _) = repo(cache, engine)
        assertEquals("磁盤譯文", repository.translate("テスト").getOrThrow())
        assertEquals(0, engine.calls)
        assertEquals("磁盤譯文", cache.memory.values.single())
    }

    @Test
    fun translate_failureIsNotCached() = runTest {
        val engine = FakeEngine(Result.failure(RuntimeException("boom")))
        val (repository, _) = repo(engine = engine)
        assertTrue(repository.translate("テスト").isFailure)
        assertTrue(repository.translate("テスト").isFailure)
        assertEquals(2, engine.calls)
    }

    @Test
    fun translate_unconfiguredYoudaoFailsFastWithoutEngineCall() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.YOUDAO, "zh-TW")
        val result = repository.translate("テスト")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TranslationConfigException)
        assertEquals(0, engine.calls)
    }

    @Test
    fun translate_unconfiguredDeeplAndMicrosoftFailFast() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.DEEPL, "zh-TW")
        assertTrue(repository.translate("テスト").exceptionOrNull() is TranslationConfigException)
        settings.config = TranslatorConfig(TranslationProvider.MICROSOFT, "zh-TW")
        assertTrue(repository.translate("テスト").exceptionOrNull() is TranslationConfigException)
        assertEquals(0, engine.calls)
    }

    @Test
    fun translate_libreWithoutUrlUsesConfiguredDefaultAndCallsEngine() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.LIBRE, "zh-TW") // baseUrl 留空 → 设置层回退默认
        assertEquals("譯文", repository.translate("テスト").getOrThrow())
        assertEquals(1, engine.calls)
    }

    @Test
    fun translate_differentTargetLangsUseSeparateCacheEntries() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.GOOGLE, "zh-TW")
        repository.translate("テスト")
        settings.config = TranslatorConfig(TranslationProvider.GOOGLE, "en")
        repository.translate("テスト")
        assertEquals(2, engine.calls)
        assertEquals("en", engine.lastTarget)
    }

    @Test
    fun translate_concurrentSameKeyCallsEngineOnce() = runTest {
        val engine = FakeEngine()
        val (repository, _) = repo(engine = engine)
        val results = (1..3).map { async { repository.translate("テスト") } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, engine.calls)
    }

    private fun key(providerId: String, source: String, target: String, text: String): String =
        "trans:$providerId:$source:$target:${sha256(text)}"

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
