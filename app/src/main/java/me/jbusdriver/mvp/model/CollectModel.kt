package me.jbusdriver.mvp.model

import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import me.jbusdriver.base.JBusManager
import me.jbusdriver.base.toast
import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.LinkItem
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType
import me.jbusdriver.ui.data.AppConfiguration

/**
 * Created by Administrator on 2018/2/13.
 */
object CollectModel {


    fun getCollectType(data: LinkItem): Int {
        return when {
            data.dbType == MovieDBType -> MovieDBType
            data.dbType == ActressDBType -> ActressDBType
            else -> 10
        }
    }

    /**
     * @return 现在无法使用返回值判断是否收藏成功
     */
    fun addToCollect(data: LinkItem): Boolean {
        DB.linkDao.insert(data)
        toast("收藏成功")

        return true
    }

    fun has(data: LinkItem) = DB.linkDao.hasByKey(data.dbType, data.key) >= 1

    fun removeCollect(data: LinkItem) = DB.linkDao.delete(data.dbType, data.key).also {
        toast("已经取消收藏")
    }


    fun update(data: LinkItem) = DB.linkDao.update(data)

    fun addToCollectForCategory(data: LinkItem, callBack: Boolean.() -> Unit = {}) {
        if (AppConfiguration.enableCategory) {
            val collectType = getCollectType(data)
            val treePrefix = when (collectType) {
                MovieDBType -> "1/"
                ActressDBType -> "2/"
                else -> "10/"
            }
            val cs = DB.categoryDao.queryTreeByLike(treePrefix + "%").blockingFirst()
            if (cs.size > 1) {
                JBusManager.manager.lastOrNull()?.get()?.let {
                    MaterialDialog(it).show {
                        title(text = "选择添加的分类")
                        listItemsSingleChoice(items = cs.map { it.name }) { dialog, index, _ ->
                            val updated = data.copy(categoryId = cs.getOrNull(index)?.id ?: -1)
                            callBack.invoke(addToCollect(updated))
                        }
                        positiveButton(text = "添加")
                    }
                    return
                }
            }
        }
        callBack.invoke(addToCollect(data))

    }
}
