package me.jbusdriver.modern.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.DeleteFileResult
import me.jbusdriver.modern.data.localvideo.LocalVideoFileDeleter
import me.jbusdriver.modern.data.localvideo.LocalVideoFileSource
import me.jbusdriver.modern.data.localvideo.LocalVideoFolderStore
import me.jbusdriver.modern.data.localvideo.scanVideoFiles
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import javax.inject.Inject
import javax.inject.Singleton

interface LocalVideoRepository {
    fun observeForCode(code: String): Flow<List<LocalVideo>>
    fun observeDownloadedCodes(): Flow<Set<String>>
    fun observeSummary(): Flow<LocalVideoSummary>
    fun hasFolder(): Flow<Boolean>
    suspend fun setFolder(uri: Uri)
    suspend fun clearFolder()
    suspend fun rescan(): Int
    fun observeAllGroupedByCode(): Flow<List<LocalVideoGroup>>
    suspend fun deleteVideos(ids: List<Int>): DeleteResult
    suspend fun snapshotMetadata(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?,
    )
}

@Singleton
class DefaultLocalVideoRepository @Inject constructor(
    private val dao: LocalVideoDao,
    private val folderStore: LocalVideoFolderStore,
    private val fileSource: LocalVideoFileSource,
    private val deleter: LocalVideoFileDeleter,
) : LocalVideoRepository {

    // 串行化重扫，避免设置页与前台观察者并发重建索引。
    private val rescanMutex = Mutex()

    override fun observeForCode(code: String): Flow<List<LocalVideo>> =
        dao.observeForCode(code.trim().uppercase())
            .map { list -> list.map { it.toDomain() } }

    override fun observeDownloadedCodes(): Flow<Set<String>> =
        dao.observeAllCodes().map { codes -> codes.map { it.uppercase() }.toSet() }

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

    override fun observeAllGroupedByCode(): Flow<List<LocalVideoGroup>> =
        dao.observeAll().map { groupLocalVideoEntities(it) }

    override suspend fun deleteVideos(ids: List<Int>): DeleteResult = rescanMutex.withLock {
        if (ids.isEmpty()) return@withLock DeleteResult(0, 0)
        val entities = dao.findByIds(ids)
        val results = entities.map { deleter.delete(it.uri) }
        val plan = planDeletion(entities, results)
        if (plan.removedIds.isNotEmpty()) dao.deleteByIds(plan.removedIds)
        DeleteResult(plan.removedIds.size, plan.failed)
    }

    override suspend fun snapshotMetadata(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?,
    ) {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return
        dao.updateSnapshot(normalized, title, imageUrl, date, censorType)
    }

    private fun LocalVideoEntity.toDomain() = LocalVideo(
        id = id,
        code = code,
        name = name,
        uri = uri,
        mime = mime,
        size = size,
        title = title,
        imageUrl = imageUrl,
        date = date,
        censorType = censorType,
    )
}

/** 删除计划：应从 DB 移除的 id，以及失败计数。 */
internal data class DeletionPlan(val removedIds: List<Int>, val failed: Int)

/** 把"逐文件删除结果"折叠为"应移除的 DB 行 + 失败数"。SUCCESS/NOT_FOUND → 移除；FAILED → 保留。 */
internal fun planDeletion(
    entities: List<LocalVideoEntity>,
    results: List<DeleteFileResult>,
): DeletionPlan {
    val removed = mutableListOf<Int>()
    var failed = 0
    for ((entity, result) in entities.zip(results)) {
        if (result == DeleteFileResult.FAILED) failed++ else removed += entity.id
    }
    return DeletionPlan(removed, failed)
}

/** 按番号分组，组内取首个非空快照字段作为代表（与排序）。纯函数，便于单测。 */
internal fun groupLocalVideoEntities(entities: List<LocalVideoEntity>): List<LocalVideoGroup> =
    entities.groupBy { it.code }
        .map { (code, list) ->
            LocalVideoGroup(
                code = code,
                title = list.firstNotNullOfOrNull { it.title },
                imageUrl = list.firstNotNullOfOrNull { it.imageUrl },
                date = list.firstNotNullOfOrNull { it.date },
                censorType = list.firstNotNullOfOrNull { it.censorType },
                files = list.map { e ->
                    LocalVideo(
                        id = e.id, code = e.code, name = e.name, uri = e.uri,
                        mime = e.mime, size = e.size, title = e.title, imageUrl = e.imageUrl,
                        date = e.date, censorType = e.censorType,
                    )
                },
            )
        }
        .sortedBy { it.code }
