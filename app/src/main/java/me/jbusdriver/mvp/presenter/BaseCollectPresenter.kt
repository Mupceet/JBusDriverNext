package me.jbusdriver.mvp.presenter

import me.jbusdriver.mvp.bean.ICollectCategory
import me.jbusdriver.db.entity.Category
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import java.util.concurrent.ConcurrentSkipListSet

interface BaseCollectPresenter<T : ICollectCategory> {

    val collectGroupMap: MutableMap<Category, List<T>>
    val adapterDelegate: CollectMultiTypeDelegate<T>


    fun setCategory(t: T, category: Category)

    class CollectMultiTypeDelegate<T : ICollectCategory> {

        /**
         * 待注入的类型
         */
        val needInjectType = ConcurrentSkipListSet<Int>()

        private val typeToLayoutMap = mutableMapOf<Int, Int>()

        fun setItemTypeInternal(type: Int, layoutResId: Int) {
            needInjectType.remove(type)
            typeToLayoutMap[type] = layoutResId
        }

        fun setItemTypeAutoIncreaseInternal(vararg layoutResIds: Int) {
            needInjectType.removeAll(0..layoutResIds.size)
            layoutResIds.forEachIndexed { index, layoutResId ->
                typeToLayoutMap[index] = layoutResId
            }
        }

        fun getItemType(item: CollectLinkWrapper<T>): Int {
            return item.level
        }

        fun getLayoutResId(itemType: Int): Int {
            return typeToLayoutMap[itemType] ?: 0
        }
    }

}