package me.jbusdriver.modern.data.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface TranslationRepository {
    /**
     * 翻译文本;主线程安全。命中内存/磁盘缓存不发请求,同键并发请求只打一次接口。
     * 失败不写缓存,返回 [Result.failure](配置无效时为 [TranslationConfigException])。
     */
    suspend fun translate(
        text: String,
        sourceLang: String = TRANSLATION_SOURCE_LANG,
        targetLang: String = TRANSLATION_TARGET_LANG,
    ): Result<String>
}

/** 依据配置创建引擎;独立成接口便于测试注入假引擎。 */
fun interface TranslatorEngineFactory {
    fun create(config: TranslatorConfig): TranslatorEngine
}

@Singleton
class DefaultTranslatorEngineFactory @Inject constructor() : TranslatorEngineFactory {
    private val googleFreeTranslator = GoogleFreeTranslator()
    override fun create(config: TranslatorConfig): TranslatorEngine = when (config.provider) {
        TranslationProvider.GOOGLE -> googleFreeTranslator
        TranslationProvider.CUSTOM -> CustomUrlTranslator(config)
    }
}

@Singleton
class DefaultTranslationRepository @Inject constructor(
    private val cacheStore: CacheStore,
    private val settings: TranslationSettingsReader,
    private val engineFactory: TranslatorEngineFactory,
) : TranslationRepository {

    /** 请求生命周期与调用方解耦:任一调用方取消都不中断共享请求(单例作用域)。 */
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<String>>>()
    private val inFlightMutex = Mutex()

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): Result<String> {
        val config = settings.currentTranslatorConfig()
        if (config.provider == TranslationProvider.CUSTOM && !config.customUrlTemplate.contains("{text}")) {
            return Result.failure(TranslationConfigException("custom url template missing {text}"))
        }
        val key = cacheKey(config.provider.id, targetLang, text)
        cacheStore.readMemory(key)?.let { return Result.success(it) }
        cacheStore.readDisk(key)?.let { cached ->
            cacheStore.writeMemory(key, cached)
            return Result.success(cached)
        }
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: requestScope.async {
                engineFactory.create(config).translate(text, sourceLang, targetLang)
                    .onSuccess {
                        cacheStore.writeMemory(key, it)
                        cacheStore.writeDisk(key, it)
                    }
                    .onFailure { KLog.t(TAG).w("translate failed: ${it.message}") }
            }.also { newDeferred ->
                inFlight[key] = newDeferred
                newDeferred.invokeOnCompletion { inFlight.remove(key, newDeferred) }
            }
        }
        return deferred.await()
    }

    private fun cacheKey(providerId: String, targetLang: String, text: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "trans:$providerId:$targetLang:$hash"
    }

    private companion object {
        const val TAG = "TranslationRepository"
    }
}
