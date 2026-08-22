package me.jbusdriver.modern.ui.debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.repository.MovieRepository
import me.jbusdriver.modern.domain.model.DataSourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 职责：debug 构建启动时抓取有码/无码影片列表前 [PAGES_PER_TYPE] 页，
 * 将影片番号以可解析格式输出到 logcat（tag [TAG]）。
 *
 * 使用场景：采集真实站点番号，作为本地视频文件名番号提取
 * （[me.jbusdriver.modern.data.localvideo.VideoCodeMatcher]）单元测试的合法用例数据；
 * 用法：启动 debug 构建，过滤 logcat tag `CodeCollector`，按 `codes=` 后的逗号分隔列表解析。
 *
 * 线程：collectOnStart 在主线程调用，内部使用独立 Supervisor 协程在 Default 调度器执行；
 * 数据层 loadPage 为主安全方法，无需额外线程切换。
 */
@Singleton
class MovieCodeSampleCollector @Inject constructor(
    private val movieRepository: MovieRepository,
    private val siteConfig: SiteConfig
) {

    fun collectOnStart() {
        if (!BuildConfig.DEBUG) return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { collect() }
    }

    private suspend fun collect() {
        siteConfig.awaitReady()
        for (type in COLLECT_TYPES) {
            val label = type.name.lowercase()
            val codes = LinkedHashSet<String>()
            for (page in 1..PAGES_PER_TYPE) {
                val pageCodes = loadPageWithRetry(type, page)
                codes += pageCodes
                KLog.d("type=$label page=$page count=${pageCodes.size} codes=${pageCodes.joinToString(",")}", TAG)
            }
            KLog.d("type=$label done total=${codes.size}", TAG)
        }
        KLog.d("collect finished", TAG)
    }

    private suspend fun loadPageWithRetry(type: DataSourceType, page: Int): List<String> {
        repeat(RETRIES) { attempt ->
            runCatching { movieRepository.loadPage(type, page).movies.map { it.code.trim() } }
                .onSuccess { return it }
                .onFailure { error ->
                    if (attempt == RETRIES - 1) {
                        KLog.d("type=${type.name.lowercase()} page=$page failed: $error", TAG)
                    } else {
                        delay(RETRY_DELAY_MS)
                    }
                }
        }
        return emptyList()
    }

    private companion object {
        const val TAG = "CodeCollector"
        const val PAGES_PER_TYPE = 18
        const val RETRIES = 3
        const val RETRY_DELAY_MS = 2_000L
        val COLLECT_TYPES = listOf(DataSourceType.CENSORED, DataSourceType.UNCENSORED)
    }
}
