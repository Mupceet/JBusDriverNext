package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.db.DB
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressDBType
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDBType
import me.jbusdriver.modern.domain.model.convertDBItem
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

    suspend fun getCollectedMovies(): List<Movie>
    suspend fun getCollectedActresses(): List<ActressInfo>
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

    override suspend fun getCollectedMovies(): List<Movie> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.listByType(MovieDBType).mapNotNull { it.getLinkValue() as? Movie }
        }
    }

    override suspend fun getCollectedActresses(): List<ActressInfo> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DB.linkDao.listByType(ActressDBType).mapNotNull { it.getLinkValue() as? ActressInfo }
        }
    }
}
