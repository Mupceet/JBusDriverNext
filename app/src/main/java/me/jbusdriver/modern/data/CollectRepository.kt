package me.jbusdriver.modern.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.data.db.DB
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressDBType
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDBType
import me.jbusdriver.modern.domain.model.convertDBItem
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
}

/**
 * 职责：收藏功能的默认实现，通过 Room DAO 操作数据库
 *
 * 使用场景：由 Hilt DataModule 绑定为 CollectRepository 的实现
 * 线程：所有方法使用 withContext(Dispatchers.IO) 在 IO 线程执行数据库操作
 */
@Singleton
class DefaultCollectRepository @Inject constructor() : CollectRepository {

    override suspend fun isCollected(linkItem: LinkItem): Boolean {
        return withContext(Dispatchers.IO) {
            DB.linkDao.hasByKey(linkItem.dbType, linkItem.key) >= 1
        }
    }

    override suspend fun addCollect(linkItem: LinkItem): Boolean {
        return withContext(Dispatchers.IO) {
            DB.linkDao.insert(linkItem)
            true
        }
    }

    override suspend fun removeCollect(linkItem: LinkItem): Boolean {
        return withContext(Dispatchers.IO) {
            DB.linkDao.delete(linkItem.dbType, linkItem.key) > 0
        }
    }

    override suspend fun isMovieCollected(movie: Movie): Boolean {
        return isCollected(movie.convertDBItem())
    }

    override suspend fun toggleMovieCollect(movie: Movie): Boolean {
        val item = movie.convertDBItem()
        return if (isCollected(item)) {
            removeCollect(item)
            false
        } else {
            addCollect(item)
            true
        }
    }

    override suspend fun isActressCollected(actress: ActressInfo): Boolean {
        return isCollected(actress.convertDBItem())
    }

    override suspend fun toggleActressCollect(actress: ActressInfo): Boolean {
        val item = actress.convertDBItem()
        return if (isCollected(item)) {
            removeCollect(item)
            false
        } else {
            addCollect(item)
            true
        }
    }

    override suspend fun getCollectedMovies(): List<Movie> {
        return withContext(Dispatchers.IO) {
            DB.linkDao.listByType(MovieDBType).mapNotNull { it.getLinkValue() as? Movie }
        }
    }

    override suspend fun getCollectedActresses(): List<ActressInfo> {
        return withContext(Dispatchers.IO) {
            DB.linkDao.listByType(ActressDBType).mapNotNull { it.getLinkValue() as? ActressInfo }
        }
    }
}
