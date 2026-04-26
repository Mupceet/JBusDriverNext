package me.jbusdriver.ui.holder

import android.content.Context
import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.addTo
import me.jbusdriver.R
import me.jbusdriver.base.SchedulersCompat
import me.jbusdriver.base.SimpleSubscriber
import me.jbusdriver.base.inflate
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieDetailActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import java.util.*

/**
 * Created by Administrator on 2017/5/9 0009.
 */
class RelativeMovieHolder(context: Context) : BaseHolder(context) {

    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_relative_movies).apply {
                val rvRecycleRelativeMovies = findViewById<RecyclerView>(R.id.rv_recycle_relative_movies)
                rvRecycleRelativeMovies.layoutManager =
                        LinearLayoutManager(it, LinearLayoutManager.HORIZONTAL, false)
                rvRecycleRelativeMovies.adapter = relativeAdapter
                rvRecycleRelativeMovies.isNestedScrollingEnabled = true
                relativeAdapter.setOnItemClickListener { _, v, position ->
                    relativeAdapter.data.getOrNull(position)?.let {
                        MovieDetailActivity.start(v.context, it)
                    }
                }
                relativeAdapter.setOnItemLongClickListener { _, view, position ->
                    relativeAdapter.data.getOrNull(position)?.let { movie ->
                        val action = (if (CollectModel.has(movie.convertDBItem())) LinkMenu.movieActions.minus("收藏")
                        else LinkMenu.movieActions.minus("取消收藏")).toMutableMap()

                        if (AppConfiguration.enableCategory) {
                            val ac = action.remove("收藏")
                            if (ac != null) {
                                action["收藏到分类..."] = ac
                            }
                        }

                        MaterialDialog(view.context).show {
                            title(text = movie.title)
                            listItems(items = action.keys.toList()) { _, index, _ ->
                                action.values.toList().getOrNull(index)?.invoke(movie)
                            }
                        }
                    }
                    return@setOnItemLongClickListener true
                }
            }
        } ?: error("context ref is finish")
    }

    private val relativeAdapter: BaseQuickAdapter<Movie, BaseViewHolder> by lazy {
        object : BaseQuickAdapter<Movie, BaseViewHolder>(R.layout.layout_detail_relative_movies_item, null) {
            override fun convert(holder: BaseViewHolder, item: Movie) {
                Glide.with(holder.itemView.context).asBitmap().load(item.imageUrl.toGlideNoHostUrl)
                    .into(object : BitmapImageViewTarget(holder.getView(R.id.iv_relative_movie_image)) {
                        override fun setResource(resource: Bitmap?) {
                            super.setResource(resource)
                            resource?.let {

                                Flowable.just(it).map {
                                    Palette.from(it).generate()
                                }.compose(SchedulersCompat.io())
                                    .subscribeWith(object : SimpleSubscriber<Palette>() {
                                        override fun onNext(it: Palette) {
                                            super.onNext(it)
                                            val swatch = listOfNotNull(
                                                it.lightMutedSwatch,
                                                it.lightVibrantSwatch,
                                                it.vibrantSwatch,
                                                it.mutedSwatch
                                            )
                                            if (!swatch.isEmpty()) {
                                                swatch[randomNum(swatch.size)].let {
                                                    holder.setBackgroundColor(R.id.tv_relative_movie_title, it.rgb)
                                                    holder.setTextColor(R.id.tv_relative_movie_title, it.bodyTextColor)
                                                }
                                            }
                                        }
                                    })
                                    .addTo(rxManager)
                            }
                        }
                    })
                holder.setText(R.id.tv_relative_movie_title, item.title)
            }
        }
    }


    private val random = Random()
    private fun randomNum(number: Int) = Math.abs(random.nextInt() % number)

    fun init(relativeMovies: List<Movie>) {
        if (relativeMovies.isEmpty()) view.findViewById<View>(R.id.tv_movie_relative_none_tip).visibility = View.VISIBLE
        else {
            relativeAdapter.setList(relativeMovies)
        }
    }

}
