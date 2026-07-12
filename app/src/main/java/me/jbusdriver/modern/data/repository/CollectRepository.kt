package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 职责：收藏功能的 Repository 接口
 *
 * 使用场景：CollectionListViewModel 和 MovieDetailViewModel 通过 Hilt 注入使用
 * 线程：所有方法为 suspend，由 ViewModel 在 viewModelScope 中调用
 */
interface CollectRepository {
    /** 检查指定 LinkItem 是否已收藏 */
    suspend fun isCollected(linkItem: LinkItem): Boolean

    /** 添加收藏 */
    suspend fun addCollect(linkItem: LinkItem): Boolean

    /** 取消收藏 */
    suspend fun removeCollect(linkItem: LinkItem): Boolean

    /** 检查电影是否已收藏 */
    suspend fun isMovieCollected(movie: Movie): Boolean

    /**
     * 切换电影收藏状态
     *
     * @return 切换后的状态：true=已收藏，false=未收藏
     */
    suspend fun toggleMovieCollect(movie: Movie, categoryId: Int? = null): Boolean

    /**
     * 更新已收藏影片的分类（用于收藏页长按标记有码/无码）。
     *
     * @param key 影片去重 key（即 `movie.link.urlPath`）
     * @param categoryId 目标分类 ID（如 [me.jbusdriver.modern.domain.model.MovieCategory] /
     *                   [me.jbusdriver.modern.domain.model.UncensoredMovieCategory]）
     * @return 是否命中并更新了至少一行
     *
     * 默认实现为空操作（返回 false），仅用于让测试桩/伪实现免于逐一实现；
     * 生产实现见 [DefaultCollectRepository.updateMovieCategory]。
     */
    suspend fun updateMovieCategory(key: String, categoryId: Int): Boolean = false

    /** 检查演员是否已收藏 */
    suspend fun isActressCollected(actress: ActressInfo): Boolean

    /**
     * 切换演员收藏状态
     *
     * @param categoryId 分类 ID；null 时使用默认演员分类（有码=2）。
     *                   无码演员传 [me.jbusdriver.modern.domain.model.UncensoredActressCategory] 的 id(4)。
     * @return 切换后的状态：true=已收藏，false=未收藏
     */
    suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int? = null): Boolean

    /** 获取所有收藏的电影列表 */
    suspend fun getCollectedMovies(): List<Movie>

    /** 获取所有收藏的演员列表 */
    suspend fun getCollectedActresses(): List<ActressInfo>

    /** 获取指定类型的原始收藏数据（包含 createTime），用于筛选和排序 */
    suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem>

    /** Export all collected movies and actresses as a JSON string (new format v1) */
    suspend fun exportCollectionsJson(): String

    /**
     * Import collections from a JSON string.
     * Supports both new format (v1) and legacy MVP format.
     * Skips items whose key already exists in the database.
     *
     * @return [imported count, skipped count]
     */
    suspend fun importCollectionsFromJson(json: String): Pair<Int, Int>
}

/**
 * 职责：收藏功能的默认实现，通过 Room DAO 操作数据库
 *
 * 使用场景：由 Hilt DataModule 绑定为 CollectRepository 的实现
 * 线程：所有方法使用 withContext(Dispatchers.IO) 在 IO 线程执行数据库操作
 */
@Singleton
class DefaultCollectRepository @Inject constructor(
    private val linkDao: LinkItemDao,
    private val siteConfig: SiteConfig,
    private val transactionRunner: CollectTransactionRunner,
    private val backupCodec: CollectionBackupCodec
) : CollectRepository {

    override suspend fun isCollected(linkItem: LinkItem): Boolean {
        return withContext(Dispatchers.IO) {
            linkDao.hasByKey(linkItem.dbType, linkItem.key) >= 1
        }
    }

    override suspend fun addCollect(linkItem: LinkItem): Boolean {
        return withContext(Dispatchers.IO) {
            linkDao.insert(linkItem) != -1L
        }
    }

    override suspend fun removeCollect(linkItem: LinkItem): Boolean {
        return withContext(Dispatchers.IO) {
            linkDao.delete(linkItem.dbType, linkItem.key) > 0
        }
    }

    override suspend fun isMovieCollected(movie: Movie): Boolean {
        return isCollected(movie.convertDBItem())
    }

    override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?): Boolean {
        val item = if (categoryId != null) movie.convertDBItem(categoryId) else movie.convertDBItem()
        return transactionRunner.withTransaction {
            val exists = linkDao.hasByKey(item.dbType, item.key) >= 1
            if (exists) {
                linkDao.delete(item.dbType, item.key)
                false
            } else {
                linkDao.insert(item)
                true
            }
        }
    }

    override suspend fun updateMovieCategory(key: String, categoryId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            linkDao.updateCategoryByKey(MovieDBType, key, categoryId) > 0
        }
    }

    override suspend fun isActressCollected(actress: ActressInfo): Boolean {
        return isCollected(actress.convertDBItem())
    }

    override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean {
        val item = if (categoryId != null) actress.convertDBItem(categoryId) else actress.convertDBItem()
        return transactionRunner.withTransaction {
            val exists = linkDao.hasByKey(item.dbType, item.key) >= 1
            if (exists) {
                linkDao.delete(item.dbType, item.key)
                false
            } else {
                linkDao.insert(item)
                true
            }
        }
    }

    override suspend fun getCollectedMovies(): List<Movie> {
        return withContext(Dispatchers.IO) {
            val baseUrl = siteConfig.baseUrl
            linkDao.listByType(MovieDBType).mapNotNull { it.toILink(baseUrl) as? Movie }
        }
    }

    override suspend fun getCollectedActresses(): List<ActressInfo> {
        return withContext(Dispatchers.IO) {
            val baseUrl = siteConfig.baseUrl
            linkDao.listByType(ActressDBType).mapNotNull { it.toILink(baseUrl) as? ActressInfo }
        }
    }

    override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> {
        return withContext(Dispatchers.IO) {
            linkDao.listByType(dbType)
        }
    }

    override suspend fun exportCollectionsJson(): String {
        return backupCodec.exportCollectionsJson()
    }

    override suspend fun importCollectionsFromJson(json: String): Pair<Int, Int> {
        return transactionRunner.withTransaction {
            backupCodec.importCollectionsFromJson(json)
        }
    }
}
