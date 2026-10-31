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
     * 翻译文本;主线程安全。源/目标语言与供应商凭据由设置解析(目标语言 = 设置值或跟随系统)。
     * 命中内存/磁盘缓存不发请求,同键并发请求只打一次接口;失败不写缓存。
     */
    suspend fun translate(text: String): Result<String>
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
        TranslationProvider.YOUDAO -> YoudaoTranslator(config.youdaoAppKey, config.youdaoAppSecret)
        TranslationProvider.LIBRE -> LibreTranslator(config.libreBaseUrl, config.libreApiKey)
        TranslationProvider.DEEPL -> DeeplTranslator(config.deeplAuthKey)
        TranslationProvider.MICROSOFT -> MicrosoftTranslator(config.microsoftKey, config.microsoftRegion)
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

    override suspend fun translate(text: String): Result<String> {
        val config = settings.currentTranslatorConfig()
        configValidationError(config)?.let { return Result.failure(it) }
        val key = cacheKey(config.provider.id, TRANSLATION_SOURCE_LANG, config.targetLang, text)
        cacheStore.readMemory(key)?.let { return Result.success(it) }
        cacheStore.readDisk(key)?.let { cached ->
            cacheStore.writeMemory(key, cached)
            return Result.success(cached)
        }
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: requestScope.async {
                engineFactory.create(config)
                    .translate(text, TRANSLATION_SOURCE_LANG, config.targetLang)
                    .onSuccess {
                        cacheStore.writeMemory(key, it)
                        cacheStore.writeDisk(key, it)
                    }
                    .onFailure { KLog.t(TAG).w("translate failed: ${it.message}") }
            }.also { newDeferred ->
                // 先发布到 inFlight 再注册完成回调:若回调先注册而请求已完成,
                // remove 落在 put 之前会导致已完成(尤其失败)的 deferred 永久滞留。
                inFlight[key] = newDeferred
                newDeferred.invokeOnCompletion { inFlight.remove(key, newDeferred) }
            }
        }
        return deferred.await()
    }

    /** 按供应商校验必填凭据;Google 无需配置,Libre 的实例 URL 由设置层回退默认值。 */
    private fun configValidationError(config: TranslatorConfig): TranslationConfigException? = when (config.provider) {
        TranslationProvider.GOOGLE -> null
        TranslationProvider.LIBRE -> null
        TranslationProvider.YOUDAO ->
            if (config.youdaoAppKey.isBlank() || config.youdaoAppSecret.isBlank()) {
                TranslationConfigException("youdao appKey/appSecret not configured")
            } else null
        TranslationProvider.DEEPL ->
            if (config.deeplAuthKey.isBlank()) TranslationConfigException("deepl auth key not configured") else null
        TranslationProvider.MICROSOFT ->
            if (config.microsoftKey.isBlank()) TranslationConfigException("microsoft key not configured") else null
    }

    private fun cacheKey(providerId: String, sourceLang: String, targetLang: String, text: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "trans:$providerId:$sourceLang:$targetLang:$hash"
    }

    private companion object {
        const val TAG = "TranslationRepository"
    }
}
