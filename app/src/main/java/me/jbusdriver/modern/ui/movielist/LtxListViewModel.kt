package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.entity.LtxMovie
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.data.ltx.LtxListQuery
import me.jbusdriver.modern.data.ltx.LtxProtocolException
import me.jbusdriver.modern.data.ltx.LtxSnapshotExpiredException
import me.jbusdriver.modern.data.parser.wrapImage
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.LtxMovieRepository
import me.jbusdriver.modern.data.repository.LtxPage
import me.jbusdriver.modern.data.repository.LtxRepository
import me.jbusdriver.modern.data.settings.CollectionUiPrefs
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import javax.inject.Inject

/**
 * 影片 tab LTX 页 ViewModel（服务端分页目录）。
 *
 * 数据源：[LtxRepository.loadPage] 按页拉取（服务端承担排序/确认状态筛选），
 * 本地仅应用站内检测筛选（[LtxResolveFilter]，来自 [LtxMovieRepository] 的
 * Room 补全快照）。编排规则：
 * - 排序/确认状态切换 → 以新参数重查第 0 页（同会话快照内，保证数据一致）；
 * - 滚动近末端 → [loadMore] 追加下一页（跨页同码去重：站点快照自带同码双行）；
 * - 下拉刷新/验证成功 → 丢弃快照重建（新快照新数据）；
 * - 快照过期（`snapshotExpired`）→ 丢快照从第 0 页重查一次；
 * - 检测筛选隐藏行导致可见条目不足时自动补拉（次数封顶防失控）。
 *
 * 补全策略（条目量大，不做全量自动扫描）：点击占位卡按需补全（插队到队首）、
 * 滚动停留窗口整体插队、滑动途经按序排队；解析经单队列限流，NOT_FOUND 终态
 * 不重试。旧「已收藏∪已下载批量补全」依赖全库扫描，分页模型下移除。
 */
@HiltViewModel
class LtxListViewModel @Inject constructor(
    private val ltxRepository: LtxRepository,
    private val ltxMovieRepository: LtxMovieRepository,
    private val ltxSettings: LtxSettingsSource,
    private val uiPrefsStore: CollectionUiPrefs,
    private val siteConfig: SiteConfig,
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

    /** 已加载页的原始条目（服务端顺序；跨页同码已去重），供本地筛选重算。 */
    private var loadedEntries: List<LtxEntry> = emptyList()

    /** 已消耗的原始行数（含被检测筛选隐藏的行），驱动下一页 offset 与 canLoadMore。 */
    private var loadedRowCount = 0

    /** 会话快照 id；null = 首查（服务端新建）/旧协议镜像（无快照）。 */
    private var snapshot: String? = null

    /** 重查代数：排序/状态切换/刷新递增，过期响应直接丢弃。 */
    private var loadGeneration = 0

    /** 筛选/排序计算代数：并发触发时只应用最新一次结果。 */
    private var computeGeneration = 0

    /** 连续自动补页计数（用户主动滚动/刷新时重置）。 */
    private var autoFillPages = 0

    private var started = false

    /** 进入 LTX 页时调用（页面激活即加载，幂等）。 */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 排序恢复（依据 + 方向）；首查免快照，服务端按恢复的排序直接出第 0 页
            val savedField = uiPrefsStore.ltxSortField.first()
            val savedDirection = uiPrefsStore.ltxSortDirection.first()
            _uiState.update {
                it.copy(
                    sortField = LtxSortField.fromName(savedField),
                    sortDirection = LtxSortDirection.fromName(savedDirection),
                )
            }

            if (!ltxSettings.ltxEnabled.first()) {
                _uiState.update {
                    it.copy(isLoading = false, items = emptyList(), totalCount = 0, canLoadMore = false)
                }
                return@launch
            }

            siteConfig.awaitReady()

            // 补全快照（Room 响应式，补全进展实时刷新 → 本地筛选重算）
            launch {
                ltxMovieRepository.observeAll().collect { rows ->
                    val baseUrl = siteConfig.baseUrl
                    currentSnapshots = withContext(ioDispatcher) { rows.toSnapshotUis(baseUrl) }
                    recompute()
                }
            }

            // 目录首页缓存先行（SWR）：冷启动即时出内容；超过新鲜期才网络重查
            val query = _uiState.value.toListQuery()
            val cached = ltxRepository.peekCachedPage(query)
            if (cached != null) publishPage(cached.page, replace = true)
            val cacheFresh = cached != null &&
                System.currentTimeMillis() - cached.storedAtMillis < LTX_CATALOG_CACHE_FRESH_MILLIS
            if (!cacheFresh) requestFirstPage(resetSnapshot = false)
        }
    }

    /** 人机验证需人工介入事件（转发自仓库），UI 据此弹出可见验证窗口。 */
    val verificationRequests: SharedFlow<Unit> = ltxRepository.verificationRequests

    /** 人工验证窗口的加载目标（LTX 站点首页）。 */
    suspend fun verificationOrigin(): String = ltxRepository.currentOrigin()

    /** 人工验证通过：注入会话使后续拉取走 OkHttp 快路径，并立即重建（新快照）。 */
    fun onVerificationSuccess(session: LtxWebSession) {
        viewModelScope.launch {
            ltxRepository.adoptWebSession(session)
            refresh()
        }
    }

    /**
     * 失败重试：清错误态并从第 0 页重查。数据管道（补全收集）由 [start] 建立
     * 且持续有效，重试只需再次拉取——不可走 [start]（幂等守卫会直接返回）。
     */
    fun retry() {
        _uiState.update { it.copy(error = null) }
        refresh()
    }

    /** 下拉刷新 / 验证成功后的重建：丢弃快照，拉取新快照的第 0 页。 */
    fun refresh() = requestFirstPage(resetSnapshot = true)

    fun updateSortField(field: LtxSortField) {
        if (_uiState.value.sortField == field) return
        _uiState.update { it.copy(sortField = field) }
        viewModelScope.launch { uiPrefsStore.setLtxSortField(field.name) }
        requestFirstPage(resetSnapshot = false)
    }

    fun updateSortDirection(direction: LtxSortDirection) {
        if (_uiState.value.sortDirection == direction) return
        _uiState.update { it.copy(sortDirection = direction) }
        viewModelScope.launch { uiPrefsStore.setLtxSortDirection(direction.name) }
        requestFirstPage(resetSnapshot = false)
    }

    fun updateStatusFilter(filter: LtxStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        requestFirstPage(resetSnapshot = false)
    }

    /**
     * 切换一个站内检测状态（已收錄/未收錄/未檢測）的选中态（多选）。
     * 关掉最后一项时回退为全选，保证列表不为空集合卡死。
     * 纯本地筛选（应用本地补全数据）：不触发网络重查。
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

    fun resetFilters() {
        val statusChanged = _uiState.value.statusFilter != LtxStatusFilter.ALL
        _uiState.update {
            it.copy(
                statusFilter = LtxStatusFilter.ALL,
                resolveFilter = LtxResolveFilter.DEFAULT,
            )
        }
        // 确认状态是服务端参数需重查；检测筛选纯本地，重算即可
        if (statusChanged) requestFirstPage(resetSnapshot = false) else recompute()
    }

    /** 滚动近末端触发：追加下一页（同会话快照内，跨页同码去重）。 */
    fun loadMore(fromAutoFill: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.isRefreshing || !state.canLoadMore) return
        if (!fromAutoFill) autoFillPages = 0
        val generation = loadGeneration
        val query = currentQuery()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = ltxRepository.loadPage(query, snapshot, loadedRowCount)
                if (generation != loadGeneration) return@launch
                publishPage(page, replace = false)
            } catch (e: LtxSnapshotExpiredException) {
                if (generation != loadGeneration) return@launch
                // 快照过期：丢弃快照从第 0 页重建当前查询
                snapshot = null
                requestFirstPage(resetSnapshot = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 翻页失败保留已加载内容并停止指示，再次滚动会重试
                KLog.t(TAG).w("LTX loadMore failed: ${e.message}")
            }
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * 从第 0 页（重）加载当前查询。[resetSnapshot]=true 丢弃既有快照（刷新/
     * 验证成功后重建数据集）；排序/状态切换复用快照（服务端在快照内重排）。
     * 快照过期自动丢快照重查一次；失败时无数据报错、有数据静默保留。
     * 查询参数在调用时点固化：快速连续切换各按各自参数重查，互不串扰。
     */
    private fun requestFirstPage(resetSnapshot: Boolean) {
        val generation = ++loadGeneration
        if (resetSnapshot) snapshot = null
        val query = _uiState.value.toListQuery()
        _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, error = null) }
        viewModelScope.launch {
            val ok = fetchFirstPage(generation, query)
            if (generation != loadGeneration) return@launch
            if (!ok) {
                _uiState.update {
                    if (it.items.isEmpty() && loadedEntries.isEmpty()) {
                        it.copy(isRefreshing = false, isLoading = false, error = it.error ?: R.string.load_failed)
                    } else {
                        it.copy(isRefreshing = false, isLoadingMore = false)
                    }
                }
            }
        }
    }

    /** 首查/重查；快照过期丢快照重试一次。返回是否成功（过期代数视为成功不重试）。 */
    private suspend fun fetchFirstPage(generation: Int, query: LtxListQuery): Boolean {
        val snap = snapshot
        return try {
            val page = ltxRepository.loadPage(query, snap, 0)
            if (generation != loadGeneration) return true
            snapshot = page.snapshot
            publishPage(page, replace = true)
            true
        } catch (e: LtxSnapshotExpiredException) {
            if (generation != loadGeneration || snap == null) return false
            KLog.t(TAG).w("LTX snapshot expired, refetching first page without snapshot")
            snapshot = null
            fetchFirstPage(generation, query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LtxProtocolException) {
            // 协议升级（加密会话通道重建失败）：重试无济于事，给出专属文案
            if (generation == loadGeneration) {
                _uiState.update { it.copy(error = R.string.ltx_protocol_upgraded) }
            }
            false
        } catch (e: Exception) {
            KLog.t(TAG).w("LTX first page failed: ${e.message}")
            false
        }
    }

    /** 页结果落地：[replace]=true 整体替换（重查），false 追加并跨页同码去重。 */
    private fun publishPage(page: LtxPage, replace: Boolean) {
        if (replace) {
            // 整表替换 = 列表内容大面积不同：作废旧列表的补全任务（新列表的
            // 补全由 UI 层停留窗口在条目发布后重新入队）
            cancelResolveWork()
            loadedEntries = page.entries
            loadedRowCount = page.entries.size
        } else {
            val seen = loadedEntries.mapTo(mutableSetOf()) { normalizeLtxCode(it.code) }
            val fresh = page.entries.filter { seen.add(normalizeLtxCode(it.code)) }
            loadedEntries = loadedEntries + fresh
            loadedRowCount += page.entries.size
        }
        _uiState.update {
            it.copy(
                totalCount = page.total,
                updatedAtMillis = page.updatedAtMillis,
                isRefreshing = false,
                isLoadingMore = false,
                canLoadMore = loadedRowCount < page.total,
            )
        }
        recompute()
    }

    /** 当前排序/状态筛选 → 站点查询参数。 */
    private fun currentQuery() = _uiState.value.toListQuery()

    /** 快照当前输入，在 IO 线程跑纯计算（本地检测筛选），代数守卫丢弃过期结果。 */
    private fun recompute() {
        val entries = loadedEntries
        val snapshots = currentSnapshots
        val resolveFilter = _uiState.value.resolveFilter
        val generation = ++computeGeneration
        viewModelScope.launch {
            val items = withContext(ioDispatcher) {
                computeLtxListItems(entries, snapshots, resolveFilter)
            }
            if (generation != computeGeneration) return@launch
            _uiState.update { it.copy(items = items, isLoading = false) }
            maybeAutoFill()
        }
    }

    /**
     * 检测筛选隐藏行导致可见条目不足时自动补拉后续页（如默认隐藏「未收錄」后
     * 首页可见数骤减）。次数封顶防失控；用户主动滚动会重置计数。
     */
    private fun maybeAutoFill() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingMore || state.isRefreshing || state.isLoading) return
        if (state.items.size >= AUTO_FILL_VISIBLE_MIN) {
            autoFillPages = 0
            return
        }
        if (autoFillPages >= MAX_AUTO_FILL_PAGES) return
        autoFillPages++
        loadMore(fromAutoFill = true)
    }

    /**
     * 点击未补全的占位卡：按需补全并回填快照。
     *
     * 交互请求**插队到队首**——若排队尾会长时间轮不到，表现为"点击无反应"。
     * 已在途/终态的调用是幂等 no-op。
     */
    fun resolveNow(code: String) {
        enqueueHead(listOf(code))
    }

    /**
     * 滑动途经补全：滑动过程中（UI 层节流）把当前窗口 [index ± viewport/2] 内的
     * 未补全番号按屏幕顺序追加到途经队列——先于停留插队之外的任务执行。
     * 不改变已有队列顺序（途经早的先处理）；已排队/在途/终态的跳过。
     */
    fun requestResolveVisible(index: Int, viewport: Int) {
        enqueueDiscovery(unresolvedCodesInWindow(index, viewport))
    }

    /**
     * 滚动停留补全：滑动停止后（UI 层去抖）把停留窗口 [index ± viewport/2] 内的
     * 未补全番号**整体插队**，优先于途经任务。与用户点击同为队首优先级。
     */
    fun requestResolveSettled(index: Int, viewport: Int) {
        enqueueHead(unresolvedCodesInWindow(index, viewport))
    }

    /** 窗口 [index ± viewport/2] 内未补全（UNRESOLVED）条目的番号，按屏幕顺序。 */
    private fun unresolvedCodesInWindow(index: Int, viewport: Int): List<String> {
        val items = _uiState.value.items
        if (items.isEmpty()) return emptyList()
        val from = (index - viewport / 2).coerceAtLeast(0)
        val to = (index + viewport / 2).coerceAtMost(items.size - 1)
        return buildList {
            for (i in from..to) {
                val item = items[i]
                if (item.resolveState == LtxResolveState.UNRESOLVED) add(item.code)
            }
        }
    }

    /** 长按复制：取该番号的全部下载链接（已确认在前、待确认在后）。 */
    fun linksFor(code: String): List<LtxLink> =
        _uiState.value.items.firstOrNull { it.code == code.uppercase() }?.links ?: emptyList()

    // region 补全队列

    /**
     * 把一批番号**插到队首**（保持给定顺序：调用方按屏幕顺序传入，这里逆序
     * addFirst）。用户点击与停留窗口走此入口；已在途/终态的跳过，
     * 两段队列中已有的先移除再插到队首。
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
            discoveryQueue.remove(normalized)
            resolveQueue.addFirst(normalized)
            changed = true
        }
        if (changed) {
            publishResolvingState()
            pumpWorkers()
        }
    }

    /** 途经发现入队（追加到途经队列尾）：已排队/在途/终态的跳过。 */
    private fun enqueueDiscovery(codes: List<String>) {
        var changed = false
        for (code in codes) {
            val normalized = code.trim().uppercase()
            if (normalized.isEmpty()) continue
            if (normalized in activeResolves) continue
            if (normalized in resolveQueue || normalized in discoveryQueue) continue
            val snapshot = currentSnapshots[normalized]
            if (snapshot != null && snapshot.resolveState != LtxResolveState.UNRESOLVED) continue
            discoveryQueue.addLast(normalized)
            changed = true
        }
        if (changed) {
            publishResolvingState()
            pumpWorkers()
        }
    }

    /** 补足并发工位：每个工位按优先级取队首解析，直到队列为空。 */
    private fun pumpWorkers() {
        while (activeResolves.size < MAX_CONCURRENT_RESOLVES) {
            val code = resolveQueue.removeFirstOrNull()
                ?: discoveryQueue.removeFirstOrNull()
                ?: break
            activeResolves.add(code)
            publishResolvingState()
            activeJobs[code] = viewModelScope.launch {
                try {
                    ltxMovieRepository.resolve(code)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // resolve 内部已把网络失败归为 UNRESOLVED，这里兜底静默
                } finally {
                    activeJobs.remove(code)
                    activeResolves.remove(code)
                    publishResolvingState()
                    pumpWorkers()
                }
            }
        }
    }

    /**
     * 整表替换（切排序/确认状态、刷新、快照重建）时作废旧列表的补全任务：
     * 取消在途请求、清空排队任务——新列表内容大面积不同，旧任务失去意义且
     * 占用工位；新列表的补全由 UI 层停留窗口在条目发布后重新入队。已落库的
     * 补全结果按码有效，不受影响。
     */
    private fun cancelResolveWork() {
        resolveQueue.clear()
        discoveryQueue.clear()
        activeJobs.values.forEach { it.cancel() }
        publishResolvingState()
    }

    private fun publishResolvingState() {
        _uiState.update {
            it.copy(resolvingCodes = activeResolves + resolveQueue + discoveryQueue)
        }
    }

    // endregion

    /** 补全快照投影（归一化番号 → 状态 + 已重建 URL 的影片快照）。 */
    private var currentSnapshots: Map<String, LtxSnapshotUi> = emptyMap()

    /** 补全队列 + 在途集合与任务（Main 线程 confined）：点击/停留 > 途经。 */
    private val resolveQueue = ArrayDeque<String>()
    private val discoveryQueue = ArrayDeque<String>()
    private val activeResolves = mutableSetOf<String>()
    private val activeJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private companion object {
        private const val TAG = "LtxListViewModel"
        const val MAX_CONCURRENT_RESOLVES = 3

        /** 可见条目低于该阈值时触发自动补页（约一屏余量）。 */
        const val AUTO_FILL_VISIBLE_MIN = 30

        /** 连续自动补页上限：防止「整页都被检测筛选隐藏」时无限拉取。 */
        const val MAX_AUTO_FILL_PAGES = 5
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

/** 目录首页缓存视为新鲜的时长（与站点 SPA 的查询内存缓存一致）；过期即重查。 */
internal const val LTX_CATALOG_CACHE_FRESH_MILLIS = 5 * 60 * 1000L
