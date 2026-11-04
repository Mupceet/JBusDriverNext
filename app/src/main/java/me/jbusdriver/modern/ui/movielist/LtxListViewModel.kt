package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.R
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.entity.LtxMovie
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.data.parser.wrapImage
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.LtxMovieRepository
import me.jbusdriver.modern.data.repository.LtxRepository
import me.jbusdriver.modern.data.settings.CollectionUiPrefs
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.Movie
import javax.inject.Inject

/**
 * 收藏 tab LTX 页 ViewModel。
 *
 * 数据源：[LtxRepository] 全库 SWR + [LtxMovieRepository] 补全快照（Room 响应式），
 * 两者合并后经纯函数 [computeLtxListItems] 产出列表。
 *
 * 补全策略（条目量大，不做全量自动扫描）：
 * - 仅「已收藏 ∪ 已下载」的番号在全库加载后自动补全；
 * - 其余条目在用户点击占位卡时按需补全并回填快照。
 * 所有解析经单队列限流（并发 [MAX_CONCURRENT_RESOLVES]），NOT_FOUND 终态不重试。
 */
@HiltViewModel
class LtxListViewModel @Inject constructor(
    private val ltxRepository: LtxRepository,
    private val ltxMovieRepository: LtxMovieRepository,
    private val ltxSettings: LtxSettingsSource,
    private val uiPrefsStore: CollectionUiPrefs,
    private val siteConfig: SiteConfig,
    private val collectRepository: CollectRepository,
    private val localVideoRepository: LocalVideoRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LtxListUiState())
    val uiState: StateFlow<LtxListUiState> = _uiState.asStateFlow()

    /** LTX 功能开关（设置），关闭时页面显示占位提示。 */
    val ltxEnabled: StateFlow<Boolean> = ltxSettings.ltxEnabled

    /** 已下载（关联本地视频）的番号集合，用于卡片角标展示。 */
    val downloadedCodes: StateFlow<Set<String>> =
        localVideoRepository.observeDownloadedCodes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 最近一次加载的全库（未筛选）。 */
    private var currentLibrary: LtxLibrary? = null

    /** 补全快照投影（归一化番号 → 状态 + 已重建 URL 的影片快照）。 */
    private var currentSnapshots: Map<String, LtxSnapshotUi> = emptyMap()

    /** 待解析队列 + 在途集合（Main 线程 confined）。 */
    private val resolveQueue = ArrayDeque<String>()
    private val activeResolves = mutableSetOf<String>()

    /** 「已收藏∪已下载」自动补全只触发一次（每次 VM 生命周期）。 */
    private var knownCodesEnqueued = false

    /** 筛选/排序计算代数：并发触发时只应用最新一次结果。 */
    private var computeGeneration = 0

    private var started = false

    /** 进入 LTX 页时调用（页面激活即加载，幂等）。 */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 排序恢复
            val savedSort = uiPrefsStore.ltxSortOption.first()
            _uiState.update { it.copy(sortOption = LtxSortOption.fromName(savedSort)) }

            if (!ltxSettings.ltxEnabled.first()) {
                _uiState.update { it.copy(isLoading = false, items = emptyList(), totalCount = 0) }
                return@launch
            }

            siteConfig.awaitReady()

            // 全库 SWR：缓存先行（Cached），过期后台刷新（Fresh）
            launch {
                ltxRepository.observeLibrary().collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> applyLibrary(event.entry.value)
                        is CachedLoadEvent.Fresh -> applyLibrary(event.entry.value)
                        is CachedLoadEvent.Failure -> applyLibraryFailure(event.hadCachedValue)
                    }
                }
            }

            // 补全快照（Room 响应式，补全进展实时刷新）
            launch {
                ltxMovieRepository.observeAll().collect { rows ->
                    val baseUrl = siteConfig.baseUrl
                    currentSnapshots = withContext(ioDispatcher) { rows.toSnapshotUis(baseUrl) }
                    recompute()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val library = ltxRepository.refresh()
            when {
                library != null -> applyLibrary(library)
                // 无缓存且刷新失败才报错；有旧数据时静默保留
                currentLibrary == null ->
                    _uiState.update { it.copy(isRefreshing = false, error = R.string.load_failed) }

                else -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun updateSort(option: LtxSortOption) {
        if (_uiState.value.sortOption == option) return
        _uiState.update { it.copy(sortOption = option) }
        recompute()
        viewModelScope.launch { uiPrefsStore.setLtxSortOption(option.name) }
    }

    fun updateStatusFilter(filter: LtxStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        recompute()
    }

    fun updateResolveFilter(filter: LtxResolveFilter) {
        if (_uiState.value.resolveFilter == filter) return
        _uiState.update { it.copy(resolveFilter = filter) }
        recompute()
    }

    fun resetFilters() {
        _uiState.update { it.copy(statusFilter = LtxStatusFilter.ALL, resolveFilter = LtxResolveFilter.ALL) }
        recompute()
    }

    /**
     * 点击未补全的占位卡：按需补全并回填快照。
     *
     * 交互请求**插队到队首**——批量任务（已收藏∪已下载）可能占满队列，
     * 若排队尾会数分钟轮不到，表现为"点击无反应"。
     * 已在途/终态的调用是幂等 no-op。
     */
    fun resolveNow(code: String) {
        enqueueResolve(code, jumpQueue = true)
    }

    /**
     * 滚动驱动的补全：把 items[index ± viewport/2] 范围内未补全的番号尾插入队。
     * 与批量任务同级排队；用户点击（[resolveNow]）仍可插队优先。
     */
    fun requestResolveAround(index: Int, viewport: Int) {
        val items = _uiState.value.items
        if (items.isEmpty()) return
        val from = (index - viewport / 2).coerceAtLeast(0)
        val to = (index + viewport / 2).coerceAtMost(items.size - 1)
        for (i in from..to) {
            val item = items[i]
            if (item.resolveState == LtxResolveState.UNRESOLVED) {
                enqueueResolve(item.code)
            }
        }
    }

    /** 长按复制：取该番号的全部下载链接（已确认在前、待确认在后）。 */
    fun linksFor(code: String): List<LtxLink> =
        _uiState.value.items.firstOrNull { it.code == code.uppercase() }?.links ?: emptyList()

    // region 补全队列

    /**
     * 入队一个番号（归一化；重复入队/在途/终态自动跳过）。
     *
     * @param jumpQueue true 时插到队首（用户交互请求优先于批量任务）
     */
    private fun enqueueResolve(code: String, jumpQueue: Boolean = false) {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        if (normalized in activeResolves) return
        val snapshot = currentSnapshots[normalized]
        if (snapshot != null && snapshot.resolveState != LtxResolveState.UNRESOLVED) return
        resolveQueue.remove(normalized)
        if (jumpQueue) resolveQueue.addFirst(normalized) else resolveQueue.addLast(normalized)
        publishResolvingState()
        pumpWorkers()
    }

    private fun enqueueResolveAll(codes: List<String>) {
        codes.forEach { enqueueResolve(it) }
    }

    /** 补足并发工位：每个工位循环取队首解析，直到队列为空。 */
    private fun pumpWorkers() {
        while (activeResolves.size < MAX_CONCURRENT_RESOLVES && resolveQueue.isNotEmpty()) {
            val code = resolveQueue.removeFirst()
            activeResolves.add(code)
            publishResolvingState()
            viewModelScope.launch {
                try {
                    ltxMovieRepository.resolve(code)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // resolve 内部已把网络失败归为 UNRESOLVED，这里兜底静默
                } finally {
                    activeResolves.remove(code)
                    publishResolvingState()
                    pumpWorkers()
                }
            }
        }
    }

    private fun publishResolvingState() {
        _uiState.update {
            it.copy(resolvingCodes = activeResolves + resolveQueue)
        }
    }

    /** 全库就绪后，自动补全「已收藏 ∪ 已下载」的番号（每次生命周期一次）。 */
    private fun enqueueKnownCodes() {
        if (knownCodesEnqueued) return
        knownCodesEnqueued = true
        viewModelScope.launch {
            val collected = runCatching { collectRepository.getCollectedMovies() }
                .getOrDefault(emptyList())
            val downloaded = runCatching { localVideoRepository.observeDownloadedCodes().first() }
                .getOrDefault(emptySet())
            val libraryCodes = currentLibrary?.entries
                ?.map { it.code.uppercase() }
                ?.toSet()
                ?: return@launch
            val known = (collected.map { it.code } + downloaded)
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() && it in libraryCodes }
                .distinct()
            enqueueResolveAll(known)
        }
    }

    // endregion

    private fun applyLibrary(library: LtxLibrary) {
        currentLibrary = library
        _uiState.update {
            it.copy(
                totalCount = library.entries.size,
                updatedAtMillis = library.updatedAtMillis,
                isLoading = false,
                isRefreshing = false,
            )
        }
        recompute()
        enqueueKnownCodes()
    }

    private fun applyLibraryFailure(hadCachedValue: Boolean) {
        if (!hadCachedValue && currentLibrary == null) {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = R.string.load_failed) }
        } else {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** 快照当前输入，在 IO 线程跑纯计算，代数守卫丢弃过期结果。 */
    private fun recompute() {
        val library = currentLibrary ?: return
        val snapshots = currentSnapshots
        val sort = _uiState.value.sortOption
        val filter = _uiState.value.statusFilter
        val resolveFilter = _uiState.value.resolveFilter
        val generation = ++computeGeneration
        viewModelScope.launch {
            val items = withContext(ioDispatcher) {
                computeLtxListItems(library, snapshots, sort, filter, resolveFilter)
            }
            if (generation != computeGeneration) return@launch
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_RESOLVES = 3
    }
}

/** 补全行 → UI 投影：movieJson 反序列化并按当前 baseUrl 重建 URL。 */
private fun List<LtxMovie>.toSnapshotUis(baseUrl: String): Map<String, LtxSnapshotUi> =
    associate { row ->
        val movie = row.movieJson.takeIf { it.isNotBlank() }
            ?.let { json -> GSON.fromJson<Movie>(json) }
            ?.let { m ->
                m.copy(
                    link = row.movieUrlPath.ifBlank { m.link }.wrapImage(baseUrl),
                    imageUrl = m.imageUrl.wrapImage(baseUrl)
                )
            }
        row.code to LtxSnapshotUi(resolveState = row.resolveState, movie = movie)
    }
