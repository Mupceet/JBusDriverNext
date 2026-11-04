package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.toJsonString
import me.jbusdriver.modern.data.db.dao.LtxMovieDao
import me.jbusdriver.modern.data.db.entity.LtxMovie
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.data.parser.stripToPath
import me.jbusdriver.modern.data.parser.wrapImage
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LTX 站内补全仓库接口。
 *
 * 职责：把 LTX 库条目按番号解析为站内影片（搜索精确匹配），并把
 * 路径化 URL 的 Movie 快照持久化到 `t_ltx_movie`，供 LTX 列表展示。
 * 所有方法 main-safe。
 */
interface LtxMovieRepository {
    /**
     * 解析一个番号并落库。
     *
     * 先按有码搜索精确匹配，未命中再按无码搜索；两轮成功但均无精确匹配记为
     * NOT_FOUND（终态）。搜索网络失败时不写终态并返回 UNRESOLVED，交由调用方重试。
     *
     * @return 解析结果状态（[LtxResolveState] 之一）。
     */
    suspend fun resolve(code: String): String

    /** 全量补全快照（响应式，补全进展实时刷新列表）。 */
    fun observeAll(): Flow<List<LtxMovie>>

    /** 按归一化番号取出已解析的 Movie 快照（URL 已按 baseUrl 重建）；未解析返回 null。 */
    suspend fun findResolvedMovie(code: String, baseUrl: String): Movie?
}

@Singleton
class DefaultLtxMovieRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val ltxMovieDao: LtxMovieDao,
    private val searchRepository: SearchRepository,
) : LtxMovieRepository {

    /** 站内搜索结果：精确匹配 / 无匹配（终态）/ 网络失败（可重试）。 */
    private sealed interface MatchOutcome {
        data class Match(val movie: Movie) : MatchOutcome
        data object NoMatch : MatchOutcome
        data class Failure(val cause: Exception) : MatchOutcome
    }

    override suspend fun resolve(code: String): String = withContext(ioDispatcher) {
        val normalized = normalizeLtxCode(code)
        if (normalized.isEmpty()) return@withContext LtxResolveState.NOT_FOUND

        when (val outcome = searchExactMatch(normalized)) {
            is MatchOutcome.Match -> {
                val movie = outcome.movie.copy(
                    link = outcome.movie.link.stripToPath(),
                    imageUrl = outcome.movie.imageUrl.stripToPath()
                )
                ltxMovieDao.upsert(
                    LtxMovie(
                        code = normalized,
                        resolveState = LtxResolveState.RESOLVED,
                        movieUrlPath = movie.link,
                        movieJson = movie.toJsonString(),
                        resolvedAt = System.currentTimeMillis(),
                    )
                )
                LtxResolveState.RESOLVED
            }

            is MatchOutcome.NoMatch -> {
                ltxMovieDao.upsert(
                    LtxMovie(
                        code = normalized,
                        resolveState = LtxResolveState.NOT_FOUND,
                        resolvedAt = System.currentTimeMillis(),
                    )
                )
                LtxResolveState.NOT_FOUND
            }

            is MatchOutcome.Failure -> {
                // 网络失败：不写终态，保持可重试
                KLog.t(TAG).e("LTX resolve search failed for $normalized", outcome.cause)
                LtxResolveState.UNRESOLVED
            }
        }
    }

    override fun observeAll(): Flow<List<LtxMovie>> = ltxMovieDao.observeAll()

    override suspend fun findResolvedMovie(code: String, baseUrl: String): Movie? =
        withContext(ioDispatcher) {
            val row = ltxMovieDao.findByCode(normalizeLtxCode(code)) ?: return@withContext null
            if (row.resolveState != LtxResolveState.RESOLVED) return@withContext null
            val movie = GSON.fromJson<Movie>(row.movieJson) ?: return@withContext null
            movie.copy(
                link = row.movieUrlPath.ifBlank { movie.link }.wrapImage(baseUrl),
                imageUrl = movie.imageUrl.wrapImage(baseUrl)
            )
        }

    /**
     * 有码 → 无码 顺序搜索。某轮网络失败立即中止（网络大概率不可用）；
     * 某轮成功但无精确匹配则继续下一轮，全部成功仍无匹配才算 NoMatch。
     */
    private suspend fun searchExactMatch(normalizedCode: String): MatchOutcome {
        for (type in SEARCH_TYPES) {
            val page = try {
                searchRepository.searchMovies(type, normalizedCode, 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return MatchOutcome.Failure(e)
            }
            val match = page.movies.firstOrNull { normalizeLtxCode(it.code) == normalizedCode }
            if (match != null) return MatchOutcome.Match(match)
        }
        return MatchOutcome.NoMatch
    }

    private companion object {
        const val TAG = "LtxMovieRepository"
        val SEARCH_TYPES = listOf(SearchType.CENSORED, SearchType.UNCENSORED)
    }
}
