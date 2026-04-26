package me.jbusdriver.base

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.module.LoadMoreModule
import com.chad.library.adapter.base.viewholder.BaseViewHolder

/**
 * Fixes BRVAH 3.0.14 bug: onBindViewHolder falls through to getItem() for the load-more footer
 * position because the LOAD_MORE_VIEW type is not checked in the when-expression.
 *
 * The fix always calls super (so autoLoadMore fires and triggers onLoadMore), but catches the
 * resulting IndexOutOfBoundsException before it propagates. autoLoadMore is invoked by BRVAH
 * before the when-expression that crashes, so the load-more callback still fires correctly.
 */
abstract class SafeBaseQuickAdapter<T>(layoutResId: Int = 0, data: MutableList<T>? = null) :
    BaseQuickAdapter<T, BaseViewHolder>(layoutResId, data), LoadMoreModule {

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        try {
            super.onBindViewHolder(holder, position)
        } catch (_: IndexOutOfBoundsException) {
        }
    }
}
