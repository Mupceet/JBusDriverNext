package me.jbusdriver.ui.holder

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.adapter.ActressInfoAdapter

/**
 * Created by Administrator on 2017/5/9 0009.
 */
class ActressListHolder(context: Context) : BaseHolder(context) {

    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_actress).apply {
                val rvRecycleActress = findViewById<RecyclerView>(R.id.rv_recycle_actress)
                rvRecycleActress.layoutManager = LinearLayoutManager(it, LinearLayoutManager.HORIZONTAL, false)
                rvRecycleActress.adapter = actressAdapter
                rvRecycleActress.isNestedScrollingEnabled = true
                actressAdapter.setOnItemClickListener { _, _, position ->
                    actressAdapter.data.getOrNull(position)?.let { item ->
                        weakRef.get()?.let {
                            MovieListActivity.start(it, item)
                        }
                    }
                }
            }
        } ?: error("context ref is finish")
    }

    private val actressAdapter by lazy {
        ActressInfoAdapter(rxManager)
    }


    fun init(actress: List<ActressInfo>) {
        if (actress.isEmpty()) view.findViewById<View>(R.id.tv_movie_actress_none_tip).visibility = View.VISIBLE
        else {
            actressAdapter.setList(actress)
        }
    }

}
