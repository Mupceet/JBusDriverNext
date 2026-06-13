package me.jbusdriver.modern.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.core.toJsonString
import androidx.room.withTransaction
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressCategory
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieCategory
import me.jbusdriver.modern.data.parser.wrapImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    suspend fun toggleMovieCollect(movie: Movie): Boolean

    /** 检查演员是否已收藏 */
    suspend fun isActressCollected(actress: ActressInfo): Boolean

    /**
     * 切换演员收藏状态
     *
     * @return 切换后的状态：true=已收藏，false=未收藏
     */
    suspend fun toggleActressCollect(actress: ActressInfo): Boolean

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
    private val siteConfig: SiteConfig
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

    override suspend fun toggleMovieCollect(movie: Movie): Boolean {
        val item = movie.convertDBItem()
        return me.jbusdriver.modern.data.db.DB.collectDatabase.withTransaction {
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

    override suspend fun isActressCollected(actress: ActressInfo): Boolean {
        return isCollected(actress.convertDBItem())
    }

    override suspend fun toggleActressCollect(actress: ActressInfo): Boolean {
        val item = actress.convertDBItem()
        return me.jbusdriver.modern.data.db.DB.collectDatabase.withTransaction {
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
            linkDao.listByType(MovieDBType).mapNotNull { it.toILink() as? Movie }
        }
    }

    override suspend fun getCollectedActresses(): List<ActressInfo> {
        return withContext(Dispatchers.IO) {
            linkDao.listByType(ActressDBType).mapNotNull { it.toILink() as? ActressInfo }
        }
    }

    override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> {
        return withContext(Dispatchers.IO) {
            linkDao.listByType(dbType)
        }
    }

    override suspend fun exportCollectionsJson(): String {
        val movies = getCollectedMovies()
        val actresses = getCollectedActresses()
        val root = JsonObject().apply {
            addProperty("version", 1)
            addProperty("exportTime", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            add("movies", JsonArray().apply {
                movies.forEach { movie ->
                    // categoryId is @Transient on Movie, must be added explicitly
                    add(GSON.fromJson(movie.toJsonString(), JsonObject::class.java).apply {
                        addProperty("categoryId", movie.categoryId)
                    })
                }
            })
            add("actresses", JsonArray().apply {
                actresses.forEach { actress ->
                    // categoryId is @Transient on ActressInfo, must be added explicitly
                    add(GSON.fromJson(actress.toJsonString(), JsonObject::class.java).apply {
                        addProperty("categoryId", actress.categoryId)
                    })
                }
            })
        }
        return GSON.toJson(root)
    }

    override suspend fun importCollectionsFromJson(json: String): Pair<Int, Int> {
        val element = GSON.fromJson(json, com.google.gson.JsonElement::class.java)
        return if (element.isJsonArray) {
            importLegacyFormat(element.asJsonArray)
        } else {
            importNewFormat(element.asJsonObject)
        }
    }

    private suspend fun importNewFormat(root: JsonObject): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        withContext(Dispatchers.IO) {
            root.getAsJsonArray("movies")?.forEach { element ->
                val jsonObj = element.asJsonObject
                val movie = GSON.fromJson(jsonObj.toString(), Movie::class.java)
                    .let { it.copy(imageUrl = it.imageUrl.wrapImage(siteConfig.baseUrl)) }
                // Restore categoryId from exported data (it's @Transient, not deserialized by Gson)
                movie.categoryId = jsonObj.get("categoryId")?.asInt ?: MovieCategory.id ?: 1
                val item = movie.convertDBItem()
                if (linkDao.hasByKey(item.dbType, item.key) >= 1) {
                    skipped++
                } else {
                    linkDao.insert(item)
                    imported++
                }
            }
            root.getAsJsonArray("actresses")?.forEach { element ->
                val jsonObj = element.asJsonObject
                val actress = GSON.fromJson(jsonObj.toString(), ActressInfo::class.java)
                    .let { it.copy(avatar = it.avatar.wrapImage(siteConfig.baseUrl)) }
                // Restore categoryId from exported data (it's @Transient, not deserialized by Gson)
                actress.categoryId = jsonObj.get("categoryId")?.asInt ?: ActressCategory.id ?: 2
                val item = actress.convertDBItem()
                if (linkDao.hasByKey(item.dbType, item.key) >= 1) {
                    skipped++
                } else {
                    linkDao.insert(item)
                    imported++
                }
            }
        }
        return imported to skipped
    }

    private suspend fun importLegacyFormat(array: JsonArray): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        withContext(Dispatchers.IO) {
            array.forEach { element ->
                val obj = element.asJsonObject
                val type = obj.get("type")?.asInt ?: return@forEach
                val jsonStr = obj.get("jsonStr")?.asString ?: return@forEach
                when (type) {
                    MovieDBType -> {
                        val movie = GSON.fromJson(jsonStr, Movie::class.java)
                            .let { it.copy(imageUrl = it.imageUrl.wrapImage(siteConfig.baseUrl)) }
                        // Legacy format may include categoryId; fallback to default
                        movie.categoryId = obj.get("categoryId")?.asInt ?: MovieCategory.id ?: 1
                        val item = movie.convertDBItem()
                        if (linkDao.hasByKey(item.dbType, item.key) >= 1) {
                            skipped++
                        } else {
                            linkDao.insert(item)
                            imported++
                        }
                    }
                    ActressDBType -> {
                        val actress = GSON.fromJson(jsonStr, ActressInfo::class.java)
                            .let { it.copy(avatar = it.avatar.wrapImage(siteConfig.baseUrl)) }
                        // Legacy format may include categoryId; fallback to default
                        actress.categoryId = obj.get("categoryId")?.asInt ?: ActressCategory.id ?: 2
                        val item = actress.convertDBItem()
                        if (linkDao.hasByKey(item.dbType, item.key) >= 1) {
                            skipped++
                        } else {
                            linkDao.insert(item)
                            imported++
                        }
                    }
                }
            }
        }
        return imported to skipped
    }
}
