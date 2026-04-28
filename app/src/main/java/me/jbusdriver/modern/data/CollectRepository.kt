package me.jbusdriver.modern.data

import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.LinkItem
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.convertDBItem
import javax.inject.Inject
import javax.inject.Singleton

interface CollectRepository {
    suspend fun isCollected(linkItem: LinkItem): Boolean
    suspend fun addCollect(linkItem: LinkItem): Boolean
    suspend fun removeCollect(linkItem: LinkItem): Boolean

    suspend fun isMovieCollected(movie: Movie): Boolean
    suspend fun toggleMovieCollect(movie: Movie): Boolean
    suspend fun isActressCollected(actress: ActressInfo): Boolean
    suspend fun toggleActressCollect(actress: ActressInfo): Boolean
}

@Singleton
class DefaultCollectRepository @Inject constructor() : CollectRepository {

    override suspend fun isCollected(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.hasByKey(linkItem.dbType, linkItem.key) >= 1
        }
    }

    override suspend fun addCollect(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.insert(linkItem)
            true
        }
    }

    override suspend fun removeCollect(linkItem: LinkItem): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
}
