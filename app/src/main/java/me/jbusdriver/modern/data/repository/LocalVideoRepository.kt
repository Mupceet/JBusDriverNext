package me.jbusdriver.modern.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.LocalVideoFileSource
import me.jbusdriver.modern.data.localvideo.LocalVideoFolderStore
import me.jbusdriver.modern.data.localvideo.scanVideoFiles
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import javax.inject.Inject
import javax.inject.Singleton

interface LocalVideoRepository {
    fun observeForCode(code: String): Flow<List<LocalVideo>>
    fun observeSummary(): Flow<LocalVideoSummary>
    fun hasFolder(): Flow<Boolean>
    suspend fun setFolder(uri: Uri)
    suspend fun clearFolder()
    suspend fun rescan(): Int
}

@Singleton
class DefaultLocalVideoRepository @Inject constructor(
    private val dao: LocalVideoDao,
    private val folderStore: LocalVideoFolderStore,
    private val fileSource: LocalVideoFileSource,
) : LocalVideoRepository {

    // 串行化重扫，避免设置页与前台观察者并发重建索引。
    private val rescanMutex = Mutex()

    override fun observeForCode(code: String): Flow<List<LocalVideo>> =
        dao.observeForCode(code.trim().uppercase())
            .map { list -> list.map { it.toDomain() } }

    override fun observeSummary(): Flow<LocalVideoSummary> =
        combine(
            dao.observeCount(),
            folderStore.folderDisplayName,
            folderStore.lastScannedAt,
        ) { count, displayName, lastScan ->
            LocalVideoSummary(count, lastScan, displayName)
        }

    override fun hasFolder(): Flow<Boolean> =
        folderStore.folderUri.map { uriStr -> uriStr != null }

    override suspend fun setFolder(uri: Uri) {
        folderStore.setFolder(uri)
        rescan()
    }

    override suspend fun clearFolder() {
        folderStore.clearFolder()
        dao.deleteAll()
    }

    override suspend fun rescan(): Int = rescanMutex.withLock {
        if (folderStore.currentFolderUri() == null) return@withLock 0
        val files = fileSource.listVideoFiles()
        val now = System.currentTimeMillis()
        val entities = scanVideoFiles(files, now)
        dao.replaceAll(entities)
        folderStore.setLastScannedAt(now)
        entities.size
    }

    private fun LocalVideoEntity.toDomain() =
        LocalVideo(code = code, name = name, uri = uri, mime = mime, size = size)
}
