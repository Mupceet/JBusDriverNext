package me.jbusdriver.modern.test

import android.content.Context
import androidx.room.Room
import me.jbusdriver.modern.data.db.CollectDatabase
import me.jbusdriver.modern.data.db.JBusDatabase
import me.jbusdriver.modern.data.db.entity.Category
import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieUiModel

/** androidTest 共用夹具：DB 实体、UI 模型工厂与内存数据库构造器。 */

fun aCategory(
    id: Int = 0,
    name: String = "cat",
    tree: String = "1/",
    order: Int = 0,
    pId: Int = -1
): Category = Category(id = id, name = name, tree = tree, order = order, pId = pId)

fun aLinkItem(
    dbType: Int = 1,
    key: String = "key",
    jsonStr: String = "{}",
    categoryId: Int = -1,
    createTime: Long = 0L,
    id: Int = 0
): LinkItem = LinkItem(
    id = id,
    dbType = dbType,
    key = key,
    jsonStr = jsonStr,
    categoryId = categoryId,
    createTime = createTime
)

fun aHistory(
    dbType: Int = 1,
    jsonStr: String = "{}",
    isAll: Int = 0,
    createTime: Long = 0L,
    id: Int = 0
): History = History(id = id, dbType = dbType, jsonStr = jsonStr, isAll = isAll, createTime = createTime)

fun aMovie(
    title: String = "movie",
    code: String = "CODE-001",
    link: String = "https://test/movie/1",
    imageUrl: String = "",
    date: String = "2024-01-01"
): MovieUiModel = MovieUiModel(title = title, imageUrl = imageUrl, code = code, date = date, link = link)

fun anActress(
    name: String = "actress",
    link: String = "https://test/actress/1",
    avatar: String = ""
): ActressUiModel = ActressUiModel(name = name, avatar = avatar, link = link)

fun aMagnet(
    name: String = "magnet-name",
    size: String = "1GB",
    date: String = "2024-01-01",
    link: String = "magnet:?xt=urn:btih:1"
): MagnetUiModel = MagnetUiModel(name = name, size = size, date = date, link = link)

fun buildCollectDb(context: Context): CollectDatabase =
    Room.inMemoryDatabaseBuilder(context, CollectDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun buildJBusDb(context: Context): JBusDatabase =
    Room.inMemoryDatabaseBuilder(context, JBusDatabase::class.java)
        .allowMainThreadQueries()
        .build()
