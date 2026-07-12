package me.jbusdriver.modern.data.localvideo

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.MovieDetailRepository
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当「显示未收藏的本地视频」开启时，后台自动为**缺少元数据快照**的本地视频番号
 * 抓取详情并回填——复用点进详情页"看过即补全"背后的逻辑（getMovieDetail + snapshotMetadata）。
 *
 * - 观察 [LocalVideoRepository.observeShowUncollectedLocal] 与 [LocalVideoRepository.observeAllGroupedByCode]；
 * - 开关开启时，串行处理 `title == null` 的番号：`getMovieDetail("/码")` -> `snapshotMetadata`；
 * - 每条 try/catch，单条失败不中断；已尝试过的码（含失败）本次会话不再重试，避免无限循环；
 * - conflate 保证总是处理最新的分组快照；开关关闭则跳过。
 *
 * 线程：自带 IO scope；[start] 在应用入口（ModernMainActivity.onCreate）调用一次，
 * 内部用 [started] 保证幂等。
 */
@Singleton
class LocalVideoMetadataEnricher @Inject constructor(
    private val localVideoRepository: LocalVideoRepository,
    private val movieDetailRepository: MovieDetailRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    /** 本次会话已尝试过的番号（成功或失败），避免对失败的番号每次发射都重试。 */
    private val attempted = mutableSetOf<String>()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                localVideoRepository.observeShowUncollectedLocal(),
                localVideoRepository.observeAllGroupedByCode(),
            ) { enabled, groups -> enabled to groups }
                .conflate()
                .collect { (enabled, groups) ->
                    if (!enabled) return@collect
                    val pending = groups.filter { it.title == null && it.code !in attempted }
                    for (group in pending) {
                        // 串行抓取过程中若用户关闭了开关，则中断本次批次
                        if (!localVideoRepository.observeShowUncollectedLocal().first()) break
                        enrich(group)
                    }
                }
        }
    }

    private suspend fun enrich(group: LocalVideoGroup) {
        attempted.add(group.code)
        runCatching {
            val detail = movieDetailRepository.getMovieDetail("/${group.code}")
            localVideoRepository.snapshotMetadata(
                code = group.code,
                title = detail.title,
                imageUrl = detail.cover,
                date = detail.headers
                    .firstOrNull { it.name in RELEASE_DATE_NAMES }
                    ?.value
                    .orEmpty(),
                censorType = null,
            )
        }.onFailure { KLog.w("Local video enrich ${group.code} failed: ${it.message}") }
    }

    private companion object {
        // 与 MovieDetailViewModel 取发行日期的表头名保持一致。
        val RELEASE_DATE_NAMES = setOf("發行日期", "日期", "发行日期")
    }
}
