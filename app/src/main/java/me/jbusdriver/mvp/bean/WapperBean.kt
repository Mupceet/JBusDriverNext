package me.jbusdriver.mvp.bean

import me.jbusdriver.db.entity.Category
import me.jbusdriver.db.service.CategoryService

/**
 * Created by Administrator on 2017/9/26 0026.
 */

class CollectLinkWrapper<T : ICollectCategory>(private val categoryDec: Category? = null, val linkBean: T? = null) {

    var isExpanded: Boolean = false
    private val _subItems: MutableList<CollectLinkWrapper<T>> = mutableListOf()
    val subItems: List<CollectLinkWrapper<T>> get() = _subItems

    fun addSubItem(item: CollectLinkWrapper<T>) {
        _subItems.add(item)
    }

    val category by lazy {
        categoryDec ?: CategoryService.getById(
            _subItems.firstOrNull()?.linkBean?.categoryId
                ?: error("category exist and  id must > 0")
        )
        ?: error("category exist and  id must > 0")
    }

    val level: Int
        get() {
            return if (linkBean == null) {
                //菜单
                category.depth.let {
                    if (it in (0..1)) 0 else it
                }
            } else {
                -1 //原item
            }
        }
}
