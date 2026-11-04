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
 * 影片 tab LTX 页 ViewModel。
 *
 * 数据源：[LtxRepository] 全库 SWR + [LtxMovieRepository] 补全快照（Room 响应式），
 * 两者合并后经纯函数 [computeLtxListItems] 产出列表。
 *
 * 补全策略（条目量大，不做全量自动扫描），优先级从高到低：
 * - 用户点击占位卡按需补全并回填快照（插队到队首）；
 * - 滚动停留窗口内的未补全条目整体插队（UI 层滑动去抖后触发）；
 * - 仅「已收藏 ∪ 已下载」的番号在全库加载后批量补全（队尾）。
 * 快速滑动途经但未停留的条目不入队。所有解析经单队列限流
 * （并发 [MAX_CONCURRENT_RESOLVES]），NOT_FOUND 终态不重试。
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

            // 排序恢复（依据 + 方向）
            val savedField = uiPrefsStore.ltxSortField.first()
            val savedDirection = uiPrefsStore.ltxSortDirection.first()
            _uiState.update {
                it.copy(
                    sortField = LtxSortField.fromName(savedField),
                    sortDirection = LtxSortDirection.fromName(savedDirection),
                )
            }

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

    fun updateSortField(field: LtxSortField) {
        if (_uiState.value.sortField == field) return
        _uiState.update { it.copy(sortField = field) }
        recompute()
        viewModelScope.launch { uiPrefsStore.setLtxSortField(field.name) }
    }

    fun updateSortDirection(direction: LtxSortDirection) {
        if (_uiState.value.sortDirection == direction) return
        _uiState.update { it.copy(sortDirection = direction) }
        recompute()
        viewModelScope.launch { uiPrefsStore.setLtxSortDirection(direction.name) }
    }

    /**
     * 切换一个站内检测状态（已收錄/未收錄/未檢測）的选中态（多选）。
     * 关掉最后一项时回退为全选，保证列表不为空集合卡死。
     */
    fun toggleResolveFilter(filter: LtxResolveFilter) {
        val current = _uiState.value.resolveFilter
        val next = if (filter in current) {
            val without = current - filter
            if (without.isEmpty()) LtxResolveFilter.entries.toSet() else without
        } else {
            current + filter
        }
        if (next == current) return
        _uiState.update { it.copy(resolveFilter = next) }
        recompute()
    }

    fun updateStatusFilter(filter: LtxStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        recompute()
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                statusFilter = LtxStatusFilter.ALL,
                resolveFilter = LtxResolveFilter.DEFAULT,
            )
        }
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
        enqueueHead(listOf(code))
    }

    /**
     * 滚动停留补全：滑动停止后（UI 层去抖）把停留窗口 [index ± viewport/2] 内的
     * 未补全番号**整体插队**，优先于路过条目与批量任务；快速滑动途经但未停留的
     * 条目不会入队。与用户点击同为队首优先级，按屏幕顺序执行。
     */
    fun requestResolveSettled(index: Int, viewport: Int) {
        val items = _uiState.value.items
        if (items.isEmpty()) return
        val from = (index - viewport / 2).coerceAtLeast(0)
        val to = (index + viewport / 2).coerceAtMost(items.size - 1)
        val codes = buildList {
            for (i in from..to) {
                val item = items[i]
                if (item.resolveState == LtxResolveState.UNRESOLVED) add(item.code)
            }
        }
        enqueueHead(codes)
    }

    /** 长按复制：取该番号的全部下载链接（已确认在前、待确认在后）。 */
    fun linksFor(code: String): List<LtxLink> =
        _uiState.value.items.firstOrNull { it.code == code.uppercase() }?.links ?: emptyList()

    // region 补全队列

    /**
     * 把一批番号**插到队首**（保持给定顺序：调用方按屏幕顺序传入，这里逆序
     * addFirst）。用户点击与停留窗口走此入口，始终先于批量任务执行；
     * 已在途/终态的跳过，队列中已有的先移除再插到队首。
     */
    private fun enqueueHead(codes: List<String>) {
        var changed = false
        for (code in codes.asReversed()) {
            val normalized = code.trim().uppercase()
            if (normalized.isEmpty()) continue
            if (normalized in activeResolves) continue
            val snapshot = currentSnapshots[normalized]
            if (snapshot != null && snapshot.resolveState != LtxResolveState.UNRESOLVED) continue
            resolveQueue.remove(normalized)
            resolveQueue.addFirst(normalized)
            changed = true
        }
        if (changed) {
            publishResolvingState()
            pumpWorkers()
        }
    }

    /** 批量任务入队（队尾）：重复入队自动跳过（队列中已有则保持原位）。 */
    private fun enqueueResolve(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        if (normalized in activeResolves) return
        val snapshot = currentSnapshots[normalized]
        if (snapshot != null && snapshot.resolveState != LtxResolveState.UNRESOLVED) return
        if (resolveQueue.contains(normalized)) return
        resolveQueue.addLast(normalized)
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
                isRefreshing = false,
                // isLoading 不在此处清除：等 recompute 完成时与 items 同帧发布，
                // 避免「库已就绪但列表仍在计算」的间隙闪现「暫無資料」。
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
        val sortField = _uiState.value.sortField
        val sortDirection = _uiState.value.sortDirection
        val filter = _uiState.value.statusFilter
        val resolveFilter = _uiState.value.resolveFilter
        val generation = ++computeGeneration
        viewModelScope.launch {
            val items = withContext(ioDispatcher) {
                computeLtxListItems(library, snapshots, sortField, sortDirection, filter, resolveFilter)
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
