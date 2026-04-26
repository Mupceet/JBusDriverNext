package me.jbusdriver.ui.adapter

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import android.text.TextUtils
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import me.jbusdriver.R
import com.bumptech.glide.Glide
import me.jbusdriver.base.SchedulersCompat
import me.jbusdriver.base.SimpleSubscriber
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import java.util.*

class ActressInfoAdapter(val rxManager: CompositeDisposable) :
    BaseQuickAdapter<ActressInfo, BaseViewHolder>(R.layout.layout_actress_item, null) {


    private val random = Random()
    private fun randomNum(number: Int) = Math.abs(random.nextInt() % number)

    override fun convert(holder: BaseViewHolder, item: ActressInfo) {
        Glide.with(holder.itemView.context).asBitmap().load(item.avatar.toGlideNoHostUrl)
            .error(R.drawable.ic_image_error)
            .into(object : BitmapImageViewTarget(holder.getView(R.id.iv_actress_avatar)) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    Flowable.just(resource).map {
                        Palette.from(resource).generate()
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
                                        holder.setBackgroundColor(R.id.tv_actress_name, it.rgb)
                                        holder.setTextColor(R.id.tv_actress_name, it.bodyTextColor)
                                    }
                                }
                            }
                        })
                        .addTo(rxManager)

                    super.onResourceReady(resource, transition)
                }
            })
        //加载名字
        holder.setText(R.id.tv_actress_name, item.name)

        holder.setText(R.id.tv_actress_tag, item.tag)
        holder.setVisible(R.id.tv_actress_tag, !TextUtils.isEmpty(item.tag))
    }

    init {

        setOnItemClickListener { _, view, position ->
            data.getOrNull(position)?.let { item ->
                MovieListActivity.start(view.context, item)
            }
        }

        setOnItemLongClickListener { _, view, position ->
            data.getOrNull(position)?.let { act ->
                val action = (if (CollectModel.has(act.convertDBItem())) LinkMenu.actressActions.minus("收藏")
                else LinkMenu.actressActions.minus("取消收藏")).toMutableMap()

                if (AppConfiguration.enableCategory) {
                    val ac = action.remove("收藏")
                    if (ac != null) {
                        action["收藏到分类..."] = ac
                    }
                }
                MaterialDialog(view.context).show {
                    title(text = act.name)
                    listItems(items = action.keys.toList()) { _, index, _ ->
                        action.values.toList().getOrNull(index)?.invoke(act)
                    }
                }
            }
            true
        }
    }


}
