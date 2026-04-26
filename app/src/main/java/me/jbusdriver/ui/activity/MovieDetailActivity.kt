package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.rxjava3.schedulers.Schedulers
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.base.urlPath
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.MovieDetailContract
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.MovieDetail
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.MovieDetailPresenterImpl
import me.jbusdriver.ui.holder.*


class MovieDetailActivity :
    AppBaseActivity<MovieDetailContract.MovieDetailPresenter, MovieDetailContract.MovieDetailView>(),
    MovieDetailContract.MovieDetailView {

    private val statusBarHeight: Int by lazy {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private lateinit var collectMenu: MenuItem
    private lateinit var removeCollectMenu: MenuItem

    private val headHolder by lazy { HeaderHolder(this) }
    private val sampleHolder by lazy { ImageSampleHolder(this) }
    private val actressHolder by lazy { ActressListHolder(this) }
    private val genreHolder by lazy { GenresHolder(this) }
    private val relativeMovieHolder by lazy { RelativeMovieHolder(this) }

    override val url by lazy { intent.getStringExtra(C.BundleKey.Key_1) }

    private lateinit var srRefresh: SwipeRefreshLayout
    private lateinit var appBar: AppBarLayout
    private lateinit var llMovieDetail: LinearLayout
    private lateinit var ivMovieCover: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            mBasePresenter?.onRefresh()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = movie?.des
        initWidget()
        initData()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_movie_detail, menu)
        collectMenu = menu.findItem(R.id.action_add_movie_collect)
        removeCollectMenu = menu.findItem(R.id.action_remove_movie_collect)
        val saveItem = movie?.convertDBItem() ?: return true
        if (CollectModel.has(saveItem)) {
            collectMenu.isVisible = false
            removeCollectMenu.isVisible = true
        } else {
            collectMenu.isVisible = true
            removeCollectMenu.isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val saveItem = movie?.convertDBItem() ?: return super.onOptionsItemSelected(item)

        when (id) {
            R.id.action_add_movie_collect -> {
                CollectModel.addToCollectForCategory(saveItem) {
                    collectMenu.isVisible = false
                    removeCollectMenu.isVisible = true
                }
            }
            R.id.action_remove_movie_collect -> {
                val result = CollectModel.removeCollect(saveItem)
                if (result != null && result > 0) {
                    collectMenu.isVisible = true
                    removeCollectMenu.isVisible = false
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initWidget() {
        srRefresh = findViewById(R.id.sr_refresh)
        appBar = findViewById(R.id.app_bar)
        llMovieDetail = findViewById(R.id.ll_movie_detail)
        ivMovieCover = findViewById(R.id.iv_movie_cover)

        srRefresh.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorPrimaryDark,
            R.color.colorPrimaryLight
        )
        srRefresh.setOnRefreshListener {
            mBasePresenter?.onRefresh()
        }

        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, offset ->
            srRefresh.isEnabled = offset >= 0
        })

        llMovieDetail.addView(headHolder.view)
        llMovieDetail.addView(sampleHolder.view)
        llMovieDetail.addView(viewContext.inflate(R.layout.layout_load_magnet).apply {
            val tvMagnet = findViewById<TextView>(R.id.tv_movie_look_magnet)
            tvMagnet.setTextColor(
                ResourcesCompat.getColor(
                    this.resources,
                    R.color.colorPrimaryDark,
                    null
                )
            )
            tvMagnet.paintFlags = tvMagnet.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                val code = movie?.code?.replace("-", " ") ?: url?.urlPath.orEmpty()
                MagnetPagerListActivity.start(this@MovieDetailActivity, code, movie?.link.orEmpty())
            }
        })
        llMovieDetail.addView(actressHolder.view)
        llMovieDetail.addView(genreHolder.view)
        llMovieDetail.addView(relativeMovieHolder.view)
    }

    private fun initData() {
        (intent.extras?.getSerializable(C.BundleKey.Key_1) as? Movie)?.let {
            movie = it
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        headHolder.release()
        sampleHolder.release()
        actressHolder.release()
        genreHolder.release()
        relativeMovieHolder.release()
    }

    override fun createPresenter() = MovieDetailPresenterImpl(
        intent?.getBooleanExtra(C.BundleKey.Key_2, false) ?: false
    )

    override val layoutId = R.layout.activity_movie_detail

    override var movie: Movie? = null


    override fun showLoading() {
        if (::srRefresh.isInitialized) {
            if (!srRefresh.isRefreshing) {
                srRefresh.post {
                    srRefresh.setProgressViewOffset(false, 0, statusBarHeight)
                    srRefresh.isRefreshing = true
                }
            }
        } else {
            super.showLoading()
        }
    }

    override fun dismissLoading() {
        if (::srRefresh.isInitialized) {
            srRefresh.post { srRefresh.isRefreshing = false }
        } else {
            super.dismissLoading()
        }
    }

    override fun <T> showContent(data: T?) {
        if (data is Movie) {
            val convertDBItem = data.convertDBItem()
            if (movie?.imageUrl != data.imageUrl && CollectModel.has(convertDBItem)) {
                Schedulers.single().scheduleDirect {
                    CollectModel.update(convertDBItem)
                }
            }
            movie = data
            invalidateOptionsMenu()
        }

        if (data is MovieDetail) {
            supportActionBar?.title = data.title
            ivMovieCover.setOnClickListener {
                WatchLargeImageActivity.startShow(
                    this,
                    listOf(data.cover) + data.imageSamples.map { it.image })
            }
            Glide.with(this)
                .load(data.cover.toGlideNoHostUrl)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(DrawableImageViewTarget(ivMovieCover))

            llMovieDetail.y = llMovieDetail.y + 120
            llMovieDetail.alpha = 0f
            llMovieDetail.visibility = View.VISIBLE
            llMovieDetail.animate().translationY(0f).alpha(1f).setDuration(500).start()

            headHolder.init(data.headers)
            sampleHolder.init(data.imageSamples)
            sampleHolder.cover = data.cover
            actressHolder.init(data.actress)
            genreHolder.init(data.genres)
            relativeMovieHolder.init(data.relatedMovies)
        }
    }

    companion object {
        fun start(current: Context, movie: Movie, fromHistory: Boolean = false) {
            current.startActivity(Intent(current, MovieDetailActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, movie)
                putExtra(C.BundleKey.Key_2, fromHistory)
            })
        }

        fun start(current: Context, movieUrl: String) {
            current.startActivity(Intent(current, MovieDetailActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, movieUrl)
                putExtra(C.BundleKey.Key_2, false)
            })
        }
    }
}
