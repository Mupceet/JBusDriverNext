package me.jbusdriver.modern.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.core.toJsonString
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.data.parser.wrapImage
import me.jbusdriver.modern.domain.model.ActressCategory
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CollectionBackupCodec @Inject constructor(
    private val linkDao: LinkItemDao,
    private val siteConfig: SiteConfig
) {
    suspend fun exportCollectionsJson(): String {
        val movies = withContext(Dispatchers.IO) { linkDao.listByType(MovieDBType) }
        val actresses = withContext(Dispatchers.IO) { linkDao.listByType(ActressDBType) }
        val baseUrl = siteConfig.baseUrl
        val root = JsonObject().apply {
            addProperty("version", 1)
            addProperty(
                "exportTime",
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            )
            add("movies", JsonArray().apply {
                movies.forEach { item ->
                    val movie = item.toILink(baseUrl) as? Movie ?: return@forEach
                    add(GSON.fromJson(movie.toJsonString(), JsonObject::class.java).apply {
                        addProperty("categoryId", item.categoryId)
                    })
                }
            })
            add("actresses", JsonArray().apply {
                actresses.forEach { item ->
                    val actress = item.toILink(baseUrl) as? ActressInfo ?: return@forEach
                    add(GSON.fromJson(actress.toJsonString(), JsonObject::class.java).apply {
                        addProperty("categoryId", item.categoryId)
                    })
                }
            })
        }
        return GSON.toJson(root)
    }

    suspend fun importCollectionsFromJson(json: String): Pair<Int, Int> {
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
                val importedCategoryId = jsonObj.get("categoryId")?.asInt ?: MovieCategory.id ?: 1
                val item = movie.convertDBItem(categoryId = importedCategoryId)
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
                val importedCategoryId = jsonObj.get("categoryId")?.asInt ?: ActressCategory.id ?: 2
                val item = actress.convertDBItem(categoryId = importedCategoryId)
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
                        val importedCategoryId =
                            obj.get("categoryId")?.asInt ?: MovieCategory.id ?: 1
                        val item = movie.convertDBItem(categoryId = importedCategoryId)
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
                        val importedCategoryId =
                            obj.get("categoryId")?.asInt ?: ActressCategory.id ?: 2
                        val item = actress.convertDBItem(categoryId = importedCategoryId)
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
