package me.jbusdriver.ui.fragment

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.common.C
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.common.RxBus
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.LinkListContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.LinkAbsPresenterImpl
import me.jbusdriver.mvp.presenter.MovieLinkPresenterImpl
import me.jbusdriver.ui.activity.SearchResultActivity

class LinkedMovieListFragment : AbsMovieListFragment(), LinkListContract.LinkListView {
    private val link by lazy {
        val link = arguments?.getSerializable(C.BundleKey.Key_1) as? ILink
            ?: error("no link data ")
        link
    }

    private val isSearch by lazy { link is SearchLink && activity != null && activity is SearchResultActivity }
    private val isHistory by lazy { arguments?.getBoolean(C.BundleKey.Key_2, false) ?: false }

    private var collectMenu: MenuItem? = null
    private var removeCollectMenu: MenuItem? = null

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val isCollect by lazy {
            CollectModel.has(link.convertDBItem())
        }
        if (!isHistory || link !is PageLink) {
            collectMenu = menu.add(Menu.NONE, R.id.action_add_movie_collect, 10, "收藏")?.apply {
                setIcon(R.drawable.ic_star_border_white_24dp)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                isVisible = !isCollect
            }
            removeCollectMenu = menu.add(Menu.NONE, R.id.action_remove_movie_collect, 10, "取消收藏")?.apply {
                setIcon(R.drawable.ic_star_white_24dp)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                isVisible = isCollect
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_add_movie_collect -> {
                CollectModel.addToCollectForCategory(link.convertDBItem()) {
                    collectMenu?.isVisible = false
                    removeCollectMenu?.isVisible = true
                }
            }
            R.id.action_remove_movie_collect -> {
                val res = CollectModel.removeCollect(link.convertDBItem())
                if (res != null && res > 0) {
                    collectMenu?.isVisible = true
                    removeCollectMenu?.isVisible = false
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun initData() {
        if (isSearch) {
            RxBus.toFlowable(SearchWord::class.java).subscribeBy { sea ->
                (mBasePresenter as? LinkAbsPresenterImpl<*>)?.let {
                    (it.linkData as SearchLink).query = sea.query
                    it.onRefresh()
                }
            }.addTo(rxManager)
        }
    }

    override fun gotoSearchResult(query: String) {
        (mBasePresenter as? LinkAbsPresenterImpl<*>)?.let {
            if (isSearch) {
                toast("新搜索 : $query")
                RxBus.post(SearchWord(query))
            } else {
                super.gotoSearchResult(query)
            }
        }
    }

    override fun createPresenter() = MovieLinkPresenterImpl(
        link, arguments?.getBoolean(LinkableListFragment.MENU_SHOW_ALL, false)
            ?: false, isHistory
    )

    override fun <T> showContent(data: T?) {
        if (data is String) {
            tempSaveBundle.putString("temp:load:all", data)
        }
        if (data is IAttr) {
            tempSaveBundle.putSerializable("temp:IAttr", data)
        }
    }

    override fun showContents(data: List<*>) {
        adapter.removeAllHeaderView()
        tempSaveBundle.getString("temp:load:all")?.let {
            getLoadAllView(it)?.let { adapter.addHeaderView(it) }
        }
        (tempSaveBundle.getSerializable("temp:IAttr") as? IAttr)?.let {
            adapter.addHeaderView(getMovieAttrView(it))
        }
        super.showContents(data)
    }

    private fun getMovieAttrView(data: IAttr): View = when (data) {
        is ActressAttrs -> {
            this.viewContext.inflate(R.layout.layout_actress_attr).apply {
                val ivAvatar = findViewById<ImageView>(R.id.iv_actress_avatar)
                val llAttrContainer = findViewById<LinearLayout>(R.id.ll_attr_container)

                Glide.with(this@LinkedMovieListFragment).load(data.imageUrl.toGlideNoHostUrl)
                    .into(DrawableImageViewTarget(ivAvatar))

                llAttrContainer.addView(generateTextView().apply {
                    textSize = 16f
                    setTextColor(resources.getColor(R.color.primaryText, null))
                    text = data.title
                })
                data.info.forEach {
                    llAttrContainer.addView(generateTextView().apply { text = it })
                }
            }
        }
        else -> error("current not provide for IAttr $data")
    }

    private fun getLoadAllView(data: String): View? {
        return data.split("：").let { txts ->
            if (txts.size == 2) {
                this.viewContext.inflate(R.layout.layout_load_all).apply {
                    val tvInfoTitle = findViewById<TextView>(R.id.tv_info_title)
                    val tvChangeA = findViewById<TextView>(R.id.tv_change_a)
                    val tvChangeB = findViewById<TextView>(R.id.tv_change_b)

                    tvInfoTitle.text = txts[0]
                    val spans = txts[1].split("，")
                    require(spans.size == 2)
                    tvChangeA.text = spans[0]
                    tvChangeB.text = spans[1]
                    tvChangeB.paintFlags = tvChangeB.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    tvChangeB.setOnClickListener {
                        val showAll = tempSaveBundle.getBoolean(MENU_SHOW_ALL)
                        mBasePresenter?.setAll(!showAll)
                        mBasePresenter?.loadData4Page(1)
                        tempSaveBundle.putBoolean(MENU_SHOW_ALL, !showAll)
                    }
                }
            } else null
        }
    }

    private fun generateTextView() = TextView(this.viewContext).apply {
        textSize = 11.5f
        setTextColor(resources.getColor(R.color.secondText, null))
    }

    companion object {
        fun newInstance(link: ILink) = LinkedMovieListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(C.BundleKey.Key_1, link)
            }
        }
    }
}
