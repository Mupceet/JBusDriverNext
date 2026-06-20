package me.jbusdriver.modern.test

import kotlinx.coroutines.flow.MutableStateFlow
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.settings.CollectionUiPrefs
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie

open class StubCollectRepository : CollectRepository {
    override suspend fun isCollected(linkItem: LinkItem): Boolean = false
    override suspend fun addCollect(linkItem: LinkItem): Boolean = true
    override suspend fun removeCollect(linkItem: LinkItem): Boolean = true
    override suspend fun isMovieCollected(movie: Movie): Boolean = false
    override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?): Boolean = true
    override suspend fun isActressCollected(actress: ActressInfo): Boolean = false
    override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean = true
    override suspend fun getCollectedMovies(): List<Movie> = emptyList()
    override suspend fun getCollectedActresses(): List<ActressInfo> = emptyList()
    override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> = emptyList()
    override suspend fun exportCollectionsJson(): String = "{}"
    override suspend fun importCollectionsFromJson(json: String): Pair<Int, Int> = 0 to 0
}

class FakeCollectionUiPrefs(
    movieSort: String = "COLLECT_DESC",
    actressSort: String = "COLLECT_DESC"
) : CollectionUiPrefs {
    override val movieSortOption = MutableStateFlow(movieSort)
    override val actressSortOption = MutableStateFlow(actressSort)

    override suspend fun setSortOption(dbType: Int, optionName: String) {
        if (dbType == 1) {
            movieSortOption.value = optionName
        } else {
            actressSortOption.value = optionName
        }
    }
}
