package me.jbusdriver.ui.holder

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.ui.adapter.GenreAdapter

/**
 * Created by Administrator on 2017/5/10 0010.
 */
class GenresHolder(context: Context) : BaseHolder(context) {
    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_genres).apply {
                val rvRecycleGenres = findViewById<RecyclerView>(R.id.rv_recycle_genres)
                rvRecycleGenres.layoutManager = FlexboxLayoutManager(context)
                rvRecycleGenres.adapter = genreAdapter
                rvRecycleGenres.isNestedScrollingEnabled = true
            }
        } ?: error("context ref is finish")
    }

    private val genreAdapter = GenreAdapter()

    fun init(genres: List<Genre>) {
        if (genres.isEmpty()) view.findViewById<View>(R.id.tv_movie_genres_none_tip).visibility = View.VISIBLE
        else {
            genreAdapter.setList(genres)
        }
    }
}
