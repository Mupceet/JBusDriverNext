package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.ForumCacheTtl
import me.jbusdriver.modern.core.cache.firstCachedOrFresh
import me.jbusdriver.modern.core.cache.observeCached
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.parser.parseDiscuzMessage
import me.jbusdriver.modern.data.parser.parseForumFloorComments
import me.jbusdriver.modern.data.parser.parseForumHomeData
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.data.parser.parseLoginForm
import me.jbusdriver.modern.data.parser.parseForumLoginState
import me.jbusdriver.modern.data.parser.parseForumUserProfile
import me.jbusdriver.modern.data.parser.parseUserFavorites
import me.jbusdriver.modern.data.parser.parseUserThreadList
import me.jbusdriver.modern.core.http.BrowserCookiePersister
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.core.http.ForumAuthCookieCleaner
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.settings.ForumAccountCache
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ForumThreadOrder
import me.jbusdriver.modern.data.settings.buildForumFindPostUrl
import me.jbusdriver.modern.data.settings.buildForumThreadDetailUrl
import me.jbusdriver.modern.data.settings.buildForumThreadListUrl
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.domain.model.ForumFavoritePageResult
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumLoginChallenge
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumUserProfile
import me.jbusdriver.modern.domain.model.ForumUserThreadPageResult
import me.jbusdriver.modern.domain.model.ForumUserThreadType
import okhttp3.FormBody
import okhttp3.Request
import android.webkit.CookieManager
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumRepo"

/** 帖子页操作链接内嵌的 formhash（#k_favorite href query）。 */
private val FORMHASH_PARAM = Regex("formhash=([a-zA-Z0-9]+)")

private fun forumLogD(message: String) {
    runCatching { KLog.d(message, TAG) }
}

private fun forumLogE(message: String) {
    runCatching { KLog.e(message, TAG) }
}

interface ForumRepository {
    /** 当前登录态（服务端实测刷新；协议依据 docs/FORUM_DESIGN.md §2.3-2）。 */
    val loginState: StateFlow<ForumLoginState>

    /** 重新拉取自空间页校准登录态，并更新账号缓存。 */
    suspend fun refreshLoginState()

    /** 若持久化 auth cookie 仍有效，用账号缓存先恢复登录态（启动秒显，不保证有效）。 */
    suspend fun tryRestoreCachedLoginState()

    /** 获取登录挑战：登录页动态表单 + seccode 验证码图片。 */
    suspend fun requestLoginChallenge(): ForumLoginChallenge

    /** 提交登录（不持久化账号密码，仅 auth cookie；D6）。失败返回 Result.failure(带站点提示文案)。 */
    suspend fun login(
        challenge: ForumLoginChallenge,
        username: String,
        password: String,
        seccode: String
    ): Result<ForumLoginState>

    /** 登出：服务端 logout（尽力而为）+ 清除本地 auth cookie 与账号缓存。 */
    suspend fun logout()

    /**
     * 收藏帖子（POST spacecp favorite；formhash 取自帖子页 #k_favorite 链接，协议 §2.4-3）。
     * [actionUrl] 为空/旧缓存缺字段时直接失败；成功与否以"我的收藏"首页重取到该 tid
     * 服务端定值（重取结果同时镜像进本地收藏库）；失败 message 为站点文案。
     */
    suspend fun addFavoriteThread(tid: Int, actionUrl: String?): Result<Unit>

    /** 本帖是否已在收藏库（Room 镜像，按当前登录账号；匿名恒 false，无网络请求）。 */
    suspend fun isFavoritedThread(tid: Int): Boolean

    suspend fun loadUserProfile(uid: Int): ForumUserProfile

    suspend fun loadUserThreads(
        uid: Int,
        type: ForumUserThreadType,
        page: Int
    ): ForumUserThreadPageResult

    suspend fun loadUserFavorites(page: Int): ForumFavoritePageResult

    /** 用户资料 SWR 流：先发缓存（即时渲染），后台刷新后发 Fresh；下拉刷新用 forceRefresh 跳过缓存。 */
    fun observeUserProfile(
        uid: Int,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true
    ): Flow<CachedLoadEvent<ForumUserProfile>>

    /** 用户主题/回复列表 SWR 流；下拉刷新用 forceRefresh 跳过缓存。 */
    fun observeUserThreads(
        uid: Int,
        type: ForumUserThreadType,
        page: Int,
        forceRefresh: Boolean = false
    ): Flow<CachedLoadEvent<ForumUserThreadPageResult>>

    /** 我的收藏列表 SWR 流；下拉刷新用 forceRefresh 跳过缓存。 */
    fun observeUserFavorites(page: Int, forceRefresh: Boolean = false): Flow<CachedLoadEvent<ForumFavoritePageResult>>

    suspend fun loadForumBoards(forceRefresh: Boolean = false): ForumHomeData
    suspend fun loadThreads(
        fid: Int,
        page: Int,
        typeId: Int? = null,
        threadOrder: ForumThreadOrder = ForumThreadOrder.LASTPOST,
        forceRefresh: Boolean = false
    ): ForumThreadPageResult

    suspend fun loadThreadDetail(
        tid: Int,
        page: Int = 1,
        floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
        authorUid: Int? = null,
        forceRefresh: Boolean = false
    ): ForumThreadDetail

    /**
     * 引用楼层定位：请求 findpost 重定向链接，跟随 302 落地到目标楼层所在分页并解析。
     * 落地页不含目标 pid（反爬墙/解析失败）时返回 null。一次性导航抓取，不走缓存。
     */
    suspend fun loadFloorJumpPage(
        tid: Int,
        pid: Int,
        floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR
    ): ForumThreadDetail?

    suspend fun loadFloorComments(
        tid: Int,
        pid: Int,
        page: Int,
        forceRefresh: Boolean = false
    ): ForumCommentPageResult

    fun observeForumBoards(
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumHomeData>>

    fun observeThreads(
        fid: Int,
        page: Int,
        typeId: Int? = null,
        threadOrder: ForumThreadOrder = ForumThreadOrder.LASTPOST,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumThreadPageResult>>

    fun observeThreadDetail(
        tid: Int,
        page: Int = 1,
        floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
        authorUid: Int? = null,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<ForumThreadDetail>>
}

@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionClient: BrowserSessionClient,
    private val cookiePersister: BrowserCookiePersister,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig,
    private val authCookieCleaner: ForumAuthCookieCleaner,
    private val accountCache: ForumAccountCache,
    private val forumFavoriteDao: me.jbusdriver.modern.data.db.dao.ForumFavoriteDao
) : ForumRepository {

    private val cookiesPersistedForForum = AtomicBoolean(false)

    private val _loginState = MutableStateFlow<ForumLoginState>(ForumLoginState.Anonymous)
    override val loginState: StateFlow<ForumLoginState> = _loginState.asStateFlow()

    override suspend fun refreshLoginState() {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        runCatching {
            val doc = fetchForumDocument("$baseUrl/forum/home.php?mod=space")
            parseForumLoginState(doc, baseUrl)
        }.onSuccess { state ->
            _loginState.value = state
            if (state is ForumLoginState.LoggedIn) {
                accountCache.save(
                    ForumAccountCache.CachedAccount(
                        uid = state.uid,
                        username = state.username,
                        avatarUrl = state.avatarUrl,
                        groupName = state.groupName
                    )
                )
                // 登录后重置一次性持久化标记，确保 auth cookie 立即落盘
                cookiesPersistedForForum.set(false)
                persistCookiesAfterFetch()
            } else {
                accountCache.clear()
            }
        }.onFailure { forumLogE("[Forum] refreshLoginState failed: ${it.message}") }
    }

    override suspend fun tryRestoreCachedLoginState() {
        if (_loginState.value !is ForumLoginState.Anonymous) return
        siteConfig.awaitReady()
        val hasAuthCookie = CookieManager.getInstance()
            .getCookie(siteConfig.referer())?.contains("4fJN_2132_auth=") == true
        if (!hasAuthCookie) return
        val cached = accountCache.cached.first()
        if (cached != null && cached.uid > 0 && cached.username.isNotBlank()) {
            _loginState.value = ForumLoginState.LoggedIn(
                cached.uid, cached.username, cached.avatarUrl, cached.groupName
            )
        }
    }

    override suspend fun requestLoginChallenge(): ForumLoginChallenge {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val loginUrl = "$baseUrl/forum/member.php?mod=logging&action=login&referer="
        val doc = fetchForumDocument(loginUrl)
        val form = parseLoginForm(doc)
            ?: throw IllegalStateException("登錄頁解析失敗，請稍後重試或改用網頁登入")
        val imageUrl = "$baseUrl/forum/misc.php?mod=seccode&update=${System.currentTimeMillis()}&idhash=${form.seccodeHash}"
        val bytes = withContext(Dispatchers.IO) {
            NetClient.glideOkHttpClient.newCall(
                Request.Builder().url(imageUrl).header("Referer", loginUrl).build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) error("驗證碼獲取失敗(${resp.code})")
                resp.body.bytes()
            }
        }
        return ForumLoginChallenge(form, bytes)
    }

    override suspend fun login(
        challenge: ForumLoginChallenge,
        username: String,
        password: String,
        seccode: String
    ): Result<ForumLoginState> {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val loginPageUrl = "$baseUrl/forum/member.php?mod=logging&action=login&referer="
        val postUrl = if (challenge.form.action.startsWith("http")) {
            challenge.form.action
        } else {
            "$baseUrl/forum/${challenge.form.action}"
        }
        val form = challenge.form
        val body = FormBody.Builder()
            .add("formhash", form.formhash)
            .add("referer", form.referer.ifBlank { "forum.php" })
            .add("loginfield", "username")
            .add("username", username)
            .add("password", password)
            .add("questionid", "0")
            .add("answer", "")
            .add("seccodehash", form.seccodeHash)
            .add("seccodemodid", "member::logging")
            .add("seccodeverify", seccode)
            .add("cookietime", "2592000")
            .add("loginsubmit", "true")
            .build()
        val responseText = withContext(Dispatchers.IO) {
            NetClient.glideOkHttpClient.newCall(
                Request.Builder().url(postUrl).header("Referer", loginPageUrl).post(body).build()
            ).execute().use { it.body.string() }
        }
        // 以服务端实测为准：auth cookie 可能因早前的浏览器登录已存在，CookieManager
        // 有它不代表"本次提交"成功；验证码对错只有提交后重新解析登录态才能确认
        refreshLoginState()
        val state = _loginState.value
        return if (state is ForumLoginState.LoggedIn) {
            Result.success(state)
        } else {
            forumLogE("[Forum] login rejected, response head: ${responseText.take(200)}")
            Result.failure(IllegalStateException(parseDiscuzMessage(responseText) ?: "登錄失敗，請檢查帳號密碼與驗證碼"))
        }
    }

    override suspend fun logout() {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        runCatching {
            // 服务端登出（logout 链接内嵌 formhash，GET 即生效）；尽力而为
            val doc = fetchForumDocument("$baseUrl/forum/forum.php")
            doc.selectFirst("a[href*='action=logout']")?.attr("href")?.let { href ->
                fetchForumDocument(absolutizeForumUrl(baseUrl, href))
            }
        }.onFailure { forumLogE("[Forum] server logout failed (ignored): ${it.message}") }
        authCookieCleaner.clearAuthCookies(siteConfig.referer())
        accountCache.clear()
        // 收藏镜像为账号私有数据，登出清空防换号串状态（下次登录后由收藏页同步重建）
        runCatching { forumFavoriteDao.clearAll() }
            .onFailure { forumLogE("[Forum] clear forum favorites failed (ignored): ${it.message}") }
        _loginState.value = ForumLoginState.Anonymous
    }

    override suspend fun isFavoritedThread(tid: Int): Boolean {
        val uid = (_loginState.value as? ForumLoginState.LoggedIn)?.uid ?: return false
        return forumFavoriteDao.isFavorite(uid, tid)
    }

    /** 收藏页解析结果镜像进本地库（正向 upsert；匿名态跳过——游客解析不出收藏列表）。 */
    private suspend fun syncFavoritesToDb(page: me.jbusdriver.modern.domain.model.ForumFavoritePageResult) {
        val uid = (_loginState.value as? ForumLoginState.LoggedIn)?.uid ?: return
        if (page.favorites.isEmpty()) return
        forumFavoriteDao.upsertAll(
            page.favorites.map {
                me.jbusdriver.modern.data.db.entity.ForumFavorite(
                    uid = uid, tid = it.tid, favId = it.favId, title = it.title, time = it.time
                )
            }
        )
    }

    override suspend fun addFavoriteThread(tid: Int, actionUrl: String?): Result<Unit> {
        if (actionUrl.isNullOrBlank()) {
            return Result.failure(IllegalStateException("該帖子暫不支持收藏"))
        }
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val referer = "$baseUrl/forum/forum.php?mod=viewthread&tid=$tid"
        // 提交字段同收藏确认窗（favorite_form）：formhash 同时存在于链接 query，兜底再取一次
        val formhash = FORMHASH_PARAM.find(actionUrl)?.groupValues?.get(1).orEmpty()
        val body = FormBody.Builder()
            .add("formhash", formhash)
            .add("favoritesubmit", "true")
            .add("description", "")
            .add("referer", "forum.php?mod=viewthread&tid=$tid")
            .build()
        val responseText = forumHttpPost(absolutizeForumUrl(baseUrl, actionUrl), referer, body)
        forumLogD("[Forum] addFavoriteThread tid=$tid response head: ${responseText.take(120)}")
        // 与登录同口径的服务端定值：重取"我的收藏"第一页（forceRefresh 跳缓存并回写），
        // 新收藏按时间倒序必在页首；不在则提交失败，message 用站点文案。
        // 重取结果无论成败都镜像进本地收藏库（已收藏场景同样落库，供下次进帖秒判）。
        return runCatching {
            val favorites = observeUserFavorites(1, forceRefresh = true).firstCachedOrFresh()
            syncFavoritesToDb(favorites)
            check(favorites.favorites.any { it.tid == tid }) {
                parseDiscuzMessage(responseText) ?: "收藏失敗，請稍後重試"
            }
        }
    }

    /** 相对链接补全为绝对 URL（站点链接均相对 /forum/ 前缀；已是绝对则原样返回）。 */
    private fun absolutizeForumUrl(baseUrl: String, href: String): String =
        if (href.startsWith("http")) href else "$baseUrl/forum/$href"

    /** 共享 CookieJar 的 OkHttp 表单 POST（与登录同通道：WebView 写、OkHttp 读）。 */
    private suspend fun forumHttpPost(url: String, referer: String, body: FormBody): String =
        withContext(Dispatchers.IO) {
            NetClient.glideOkHttpClient.newCall(
                Request.Builder().url(url).header("Referer", referer).post(body).build()
            ).execute().use { it.body.string() }
        }

    override suspend fun loadUserProfile(uid: Int): ForumUserProfile =
        observeUserProfile(uid).firstCachedOrFresh()

    override suspend fun loadUserThreads(
        uid: Int,
        type: ForumUserThreadType,
        page: Int
    ): ForumUserThreadPageResult =
        observeUserThreads(uid, type, page).firstCachedOrFresh()

    override suspend fun loadUserFavorites(page: Int): ForumFavoritePageResult =
        observeUserFavorites(page).firstCachedOrFresh()

    private fun userSpaceId(uid: Int): String = if (uid > 0) uid.toString() else "self"

    override fun observeUserProfile(
        uid: Int,
        forceRefresh: Boolean,
        revalidate: Boolean
    ): Flow<CachedLoadEvent<ForumUserProfile>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = if (uid > 0) "$baseUrl/forum/home.php?mod=space&uid=$uid" else "$baseUrl/forum/home.php?mod=space"
        forumLogD("[Forum] observeUserProfile: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = "${forumCachePrefix(baseUrl)}:user-profile:${userSpaceId(uid)}",
                ttlMillis = ForumCacheTtl.HOME_MILLIS,
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate,
                // 游客视角的登录重定向页用户名为空，不可缓存
                isCacheable = { it.username.isNotBlank() }
            ) {
                parseForumUserProfile(fetchForumDocument(url), baseUrl)
            }
        )
    }

    override fun observeUserThreads(
        uid: Int,
        type: ForumUserThreadType,
        page: Int,
        forceRefresh: Boolean
    ): Flow<CachedLoadEvent<ForumUserThreadPageResult>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = "$baseUrl/forum/home.php?mod=space&uid=$uid&do=thread&view=me&type=${type.param}&from=space&page=$page"
        forumLogD("[Forum] observeUserThreads: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = "${forumCachePrefix(baseUrl)}:user-threads:$uid:${type.param}:$page",
                ttlMillis = threadListTtl(page),
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = page == 1,
                isCacheable = { it.threads.isNotEmpty() }
            ) {
                parseUserThreadList(fetchForumDocument(url))
            }
        )
    }

    override fun observeUserFavorites(page: Int, forceRefresh: Boolean): Flow<CachedLoadEvent<ForumFavoritePageResult>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = "$baseUrl/forum/home.php?mod=space&do=favorite&view=me&type=thread&page=$page"
        forumLogD("[Forum] observeUserFavorites: url=$url")
        emitAll(
            cacheStore.observeCached(
                // 收藏为登录私有数据：key 绑定当前登录 uid，换号/登出后不串缓存
                key = "${forumCachePrefix(baseUrl)}:user-favorites:${_loginState.value.let { (it as? ForumLoginState.LoggedIn)?.uid ?: 0 }}:$page",
                ttlMillis = threadListTtl(page),
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = page == 1,
                isCacheable = { it.favorites.isNotEmpty() }
            ) {
                val page = parseUserFavorites(fetchForumDocument(url))
                // 服务端收藏页 → 本地镜像（正向 upsert，个人主页浏览即同步，不限首页）
                syncFavoritesToDb(page)
                page
            }
        )
    }

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchDocument(url)
        forumLogD("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}")
        persistCookiesAfterFetch()
        return doc
    }

    private suspend fun fetchForumAjaxDocument(
        url: String,
        referer: String
    ): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchAjaxDocument(url, referer)
        forumLogD("[Forum] ajax fetched: title=${doc.title()}, length=${doc.html().length}")
        persistCookiesAfterFetch()
        return doc
    }

    private suspend fun persistCookiesAfterFetch() {
        // Persist cookies after first successful forum page fetch
        // to capture Discuz! session cookies (4fJN_2132_*)
        if (cookiesPersistedForForum.compareAndSet(false, true)) {
            try {
                cookiePersister.persistCookies()
            } catch (e: Exception) {
                forumLogE("[Forum] Failed to persist cookies: ${e.message}")
                cookiesPersistedForForum.set(false)
            }
        }
    }

    override fun observeForumBoards(
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumHomeData>> = flow {
        // 等待站点配置就绪，避免冷启动时用默认域名构建 URL/缓存 key（用户可能已选镜像）。
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = "$baseUrl/forum/forum.php"
        forumLogD("[Forum] loadForumBoards: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = forumBoardsCacheKey(baseUrl),
                ttlMillis = ForumCacheTtl.HOME_MILLIS,
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate,
                nowMillis = nowMillis,
                isCacheable = { it.boardGroups.isNotEmpty() }
            ) {
                val doc = fetchForumDocument(url)
                val result = parseForumHomeData(doc, baseUrl)
                forumLogD("[Forum] parseForumHomeData: ${result.banners.size} banners, ${result.boardGroups.sumOf { it.boards.size }} boards")
                result
            }
        )
    }

    override fun observeThreads(
        fid: Int,
        page: Int,
        typeId: Int?,
        threadOrder: ForumThreadOrder,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = buildForumThreadListUrl(baseUrl, fid, page, typeId, threadOrder)
        forumLogD("[Forum] loadThreads: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = forumThreadsCacheKey(baseUrl, fid, page, typeId, threadOrder),
                ttlMillis = threadListTtl(page),
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate && page == 1,
                nowMillis = nowMillis
            ) {
                val doc = fetchForumDocument(url)
                parseForumThreads(doc, baseUrl)
            }
        )
    }

    override fun observeThreadDetail(
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        authorUid: Int?,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<ForumThreadDetail>> = flow {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = buildForumThreadDetailUrl(baseUrl, tid, page, floorOrder, authorUid)
        forumLogD("[Forum] loadThreadDetail: url=$url")
        emitAll(
            cacheStore.observeCached(
                key = forumDetailCacheKey(baseUrl, tid, page, floorOrder, authorUid),
                ttlMillis = threadDetailTtl(page),
                disk = true,
                forceRefresh = forceRefresh,
                revalidate = revalidate && page == 1,
                nowMillis = nowMillis
            ) {
                val doc = fetchForumDocument(url)
                parseForumThreadDetail(doc, baseUrl)
            }
        )
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData =
        observeForumBoards(forceRefresh = forceRefresh, revalidate = false).firstCachedOrFresh()

    override suspend fun loadThreads(
        fid: Int,
        page: Int,
        typeId: Int?,
        threadOrder: ForumThreadOrder,
        forceRefresh: Boolean
    ): ForumThreadPageResult =
        observeThreads(
            fid,
            page,
            typeId,
            threadOrder,
            forceRefresh = forceRefresh,
            revalidate = false
        ).firstCachedOrFresh()

    override suspend fun loadThreadDetail(
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        authorUid: Int?,
        forceRefresh: Boolean
    ): ForumThreadDetail =
        observeThreadDetail(
            tid,
            page,
            floorOrder,
            authorUid = authorUid,
            forceRefresh = forceRefresh,
            revalidate = false
        ).firstCachedOrFresh()

    override suspend fun loadFloorJumpPage(
        tid: Int,
        pid: Int,
        floorOrder: ForumFloorOrder
    ): ForumThreadDetail? {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = buildForumFindPostUrl(baseUrl, tid, pid, floorOrder)
        forumLogD("[Forum] loadFloorJumpPage: url=$url")
        val doc = fetchForumDocument(url)
        val detail = parseForumThreadDetail(doc, baseUrl)
        return detail.takeIf { it.pid == pid || it.replies.any { it.pid == pid } }
    }

    override suspend fun loadFloorComments(
        tid: Int,
        pid: Int,
        page: Int,
        forceRefresh: Boolean
    ): ForumCommentPageResult {
        siteConfig.awaitReady()
        val baseUrl = siteConfig.baseUrl
        val url = "$baseUrl/forum/forum.php?mod=misc&action=commentmore&tid=$tid&pid=$pid&page=$page&inajax=1&ajaxtarget=comment_$pid"
        val referer = "$baseUrl/forum/forum.php?mod=viewthread&tid=$tid"
        forumLogD("[Forum] loadFloorComments: url=$url")
        return cacheStore.observeCached(
            key = forumFloorCommentsCacheKey(baseUrl, tid, pid, page),
            ttlMillis = ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = false
        ) {
            val doc = fetchForumAjaxDocument(url, referer)
            parseForumFloorComments(doc, baseUrl, pid)
        }.firstCachedOrFresh()
    }

    private fun forumCachePrefix(baseUrl: String): String = "forum:$baseUrl"

    private fun forumBoardsCacheKey(baseUrl: String): String = "${forumCachePrefix(baseUrl)}:boards"

    private fun forumThreadsCacheKey(
        baseUrl: String,
        fid: Int,
        page: Int,
        typeId: Int?,
        threadOrder: ForumThreadOrder
    ): String = "${forumCachePrefix(baseUrl)}:threads:$fid:$page:${typeId ?: "all"}:${threadOrder.cacheValue}"

    private fun forumDetailCacheKey(
        baseUrl: String,
        tid: Int,
        page: Int,
        floorOrder: ForumFloorOrder,
        authorUid: Int?
    ): String {
        val base = "${forumCachePrefix(baseUrl)}:detail:v3:$tid:$page:${floorOrder.name.lowercase()}"
        return if (authorUid != null && authorUid > 0) "$base:author$authorUid" else base
    }

    private fun forumFloorCommentsCacheKey(
        baseUrl: String,
        tid: Int,
        pid: Int,
        page: Int
    ): String = "${forumCachePrefix(baseUrl)}:floor-comments:v2:$tid:$pid:$page"

    private fun threadListTtl(page: Int): Long =
        if (page == 1) ForumCacheTtl.THREAD_LIST_FIRST_PAGE_MILLIS
        else ForumCacheTtl.THREAD_LIST_NEXT_PAGE_MILLIS

    private fun threadDetailTtl(page: Int): Long =
        if (page == 1) ForumCacheTtl.THREAD_DETAIL_FIRST_PAGE_MILLIS
        else ForumCacheTtl.THREAD_DETAIL_NEXT_PAGE_MILLIS

}
