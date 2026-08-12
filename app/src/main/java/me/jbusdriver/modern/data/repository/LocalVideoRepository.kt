package me.jbusdriver.modern.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.DeleteFileResult
import me.jbusdriver.modern.data.localvideo.LocalVideoFileDeleter
import me.jbusdriver.modern.data.localvideo.LocalVideoFileSource
import me.jbusdriver.modern.data.localvideo.LocalVideoFolderStore
import me.jbusdriver.modern.data.localvideo.UNCLASSIFIED_CODE
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
    fun observeShowUncollectedLocal(): Flow<Boolean>
    suspend fun setShowUncollectedLocal(value: Boolean)
    suspend fun deleteVideos(ids: List<Int>): DeleteResult
    suspend fun snapshotMetadata(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?,
    )

    /** 用户排除（不在收藏页未收藏分区展示）的本地视频 URI 集合。 */
    fun observeExcludedUris(): Flow<Set<String>> = flowOf(emptySet())

    /** 把一批本地视频 URI 加入/移出排除集合。 */
    suspend fun setExcludedUris(uris: Set<String>, excluded: Boolean) = Unit

    /** 全部本地视频（含未分类），用于设置页"已排除"管理。 */
    fun observeAllLocalVideos(): Flow<List<LocalVideo>> = flowOf(emptyList())
}

@Singleton
class DefaultLocalVideoRepository @Inject constructor(
    private val dao: LocalVideoDao,
    private val folderStore: LocalVideoFolderStore,
    private val fileSource: LocalVideoFileSource,
    private val deleter: LocalVideoFileDeleter,
    private val collectRepository: CollectRepository,
) : LocalVideoRepository {

    // 串行化重扫，避免设置页与前台观察者并发重建索引。
    private val rescanMutex = Mutex()

    override fun observeForCode(code: String): Flow<List<LocalVideo>> =
        dao.observeForCode(code.trim().uppercase())
            .map { list -> list.map { it.toDomain() } }

    override fun observeDownloadedCodes(): Flow<Set<String>> =
        dao.observeAllCodes()
            .map { codes -> codes.filter { it.isNotBlank() }.map { it.uppercase() }.toSet() }

    override fun observeSummary(): Flow<LocalVideoSummary> = combine(
        dao.observeLinkedCount(),
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
        // 收藏影片番号已确认：`-`/`_` 等价匹配，命中即归属该番号。
        val collectedCodes = collectRepository.getCollectedMovies().map { it.code }.toSet()
        val scanned = scanVideoFiles(files, collectedCodes, now)
        val withSnapshots = preserveSnapshots(scanned, dao.getAll())
        dao.replaceAll(withSnapshots)
        folderStore.setLastScannedAt(now)
        withSnapshots.size
    }

    override fun observeAllGroupedByCode(): Flow<List<LocalVideoGroup>> =
        dao.observeAll().map { groupLocalVideoEntities(it) }

    override fun observeShowUncollectedLocal(): Flow<Boolean> = folderStore.showUncollectedLocal

    override suspend fun setShowUncollectedLocal(value: Boolean) {
        folderStore.setShowUncollectedLocal(value)
    }

    override fun observeExcludedUris(): Flow<Set<String>> = folderStore.excludedUris

    override suspend fun setExcludedUris(uris: Set<String>, excluded: Boolean) {
        folderStore.setExcludedUris(uris, excluded)
    }

    override fun observeAllLocalVideos(): Flow<List<LocalVideo>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

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
        rescanMutex.withLock { dao.updateSnapshot(normalized, title, imageUrl, date, censorType) }
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

/** 把逐文件删除结果折叠为"应移除的 DB 行 + 失败数"。SUCCESS/NOT_FOUND → 移除；FAILED → 保留。 */
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

/**
 * 按番号分组，组内取首个非空快照字段作为代表（与排序一致）。纯函数，便于单测。
 *
 * 未分类（code 为空串）的文件没有确认番号，每个文件独立成组，
 * 供收藏页未收藏分区以"单文件卡片"展示并支持逐个排除。
 */
internal fun groupLocalVideoEntities(entities: List<LocalVideoEntity>): List<LocalVideoGroup> =
    entities
        .groupBy { it.code }
        .flatMap { (code, list) ->
            if (code == UNCLASSIFIED_CODE) {
                list.map { it.toGroup() }
            } else {
                listOf(
                    LocalVideoGroup(
                        code = code,
                        title = list.firstNotNullOfOrNull { it.title },
                        imageUrl = list.firstNotNullOfOrNull { it.imageUrl },
                        date = list.firstNotNullOfOrNull { it.date },
                        censorType = list.firstNotNullOfOrNull { it.censorType },
                        files = list.map { it.toLocalVideo() },
                    )
                )
            }
        }
        .sortedBy { it.code }

/**
 * 重扫后按 code 继承上次已回填的元数据快照，避免重扫清空"看过即补全"的数据。纯函数。
 * 未分类（空串 code）不参与快照继承。
 */
internal fun preserveSnapshots(
    scanned: List<LocalVideoEntity>,
    existing: List<LocalVideoEntity>,
): List<LocalVideoEntity> {
    val snapshotByCode = existing.filter { it.code.isNotBlank() }
        .groupBy { it.code }
        .mapValues { (_, list) -> list.firstOrNull { it.title != null } ?: list.first() }
    return scanned.map { e ->
        if (e.code.isBlank()) e
        else {
            val snap = snapshotByCode[e.code]
            if (snap != null && snap.title != null) {
                e.copy(title = snap.title, imageUrl = snap.imageUrl, date = snap.date, censorType = snap.censorType)
            } else e
        }
    }
}

private fun LocalVideoEntity.toGroup(): LocalVideoGroup = LocalVideoGroup(
    code = code,
    title = title,
    imageUrl = imageUrl,
    date = date,
    censorType = censorType,
    files = listOf(toLocalVideo()),
)

private fun LocalVideoEntity.toLocalVideo() = LocalVideo(
    id = id, code = code, name = name, uri = uri,
    mime = mime, size = size, title = title, imageUrl = imageUrl,
    date = date, censorType = censorType,
)
