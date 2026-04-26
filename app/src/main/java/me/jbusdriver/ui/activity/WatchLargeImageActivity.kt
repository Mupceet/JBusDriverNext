package me.jbusdriver.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.common.toGlideNoHostUrl
import java.io.File
import kotlin.random.Random


class WatchLargeImageActivity : BaseActivity() {

    private val urls by lazy {
        intent.getStringArrayListExtra(INTENT_IMAGE_URL) ?: emptyList<String>()
    }
    private val imageViewList: ArrayList<View> = arrayListOf()
    private val index by lazy { intent.getIntExtra(INDEX, -1) }
    private val imageSaveDir by lazy {
        val packageName = JBusManager.context.packageName
        val pathSuffix = File.separator + "download" + File.separator + "image" + File.separator
        createDir(Environment.getExternalStorageDirectory().absolutePath + File.separator + packageName + pathSuffix)
            ?: createDir(JBusManager.context.cacheDir.absolutePath + packageName + pathSuffix)
            ?: error("cant not create collect dir in anywhere")
    }

    private lateinit var vpLargeImage: ViewPager
    private lateinit var tvUrlIndex: TextView
    private lateinit var ivDownload: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_large_image)
        vpLargeImage = findViewById(R.id.vp_largeImage)
        tvUrlIndex = findViewById(R.id.tv_url_index)
        ivDownload = findViewById(R.id.iv_download)
        initWidget()
    }

    @SuppressLint("SetTextI18n")
    private fun initWidget() {
        urls.mapTo(imageViewList) {
            this@WatchLargeImageActivity.inflate(R.layout.layout_large_image_item).apply {
                val pb = findViewById<ProgressBar>(R.id.pb_hor_progress)
                (pb.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = statusBarHeight
            }
        }

        vpLargeImage.adapter = MyViewPagerAdapter()
        vpLargeImage.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                tvUrlIndex.text = "${position + 1} / ${imageViewList.size}"
            }
        })
        vpLargeImage.currentItem = if (index == -1) 0 else index
        tvUrlIndex.text = "${vpLargeImage.currentItem + 1} / ${imageViewList.size}"

        ivDownload.setOnClickListener {
            val url = urls[vpLargeImage.currentItem]
            val fileName = url.urlPath.split("/").lastOrNull()
                ?: "${System.currentTimeMillis()}-${(Random(System.currentTimeMillis()).nextFloat() * 1000).toInt()}.jpg"
            Single.fromFuture(Glide.with(this).download(url).submit())
                .doOnSuccess { source ->
                    val target = File(imageSaveDir + fileName)
                    source.copyTo(target, true)
                }.subscribeOn(Schedulers.io())
                .subscribeBy {
                    toast("文件保存至${imageSaveDir}下")
                }
                .addTo(rxManager)
        }
    }

    private val statusBarHeight: Int by lazy {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    companion object {
        private const val INTENT_IMAGE_URL = "INTENT_IMAGE_URL"
        private const val INDEX = "currentIndex"

        fun startShow(context: Context, urls: List<String>, index: Int = -1) {
            val intent = Intent(context, WatchLargeImageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putStringArrayListExtra(INTENT_IMAGE_URL, ArrayList(urls))
            intent.putExtra(INDEX, index)
            context.startActivity(intent)
        }
    }

    inner class MyViewPagerAdapter : PagerAdapter() {

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(imageViewList[position])
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            return imageViewList.getOrNull(position)?.apply {
                container.addView(this, 0)
                loadImage(this, position)
            } ?: error("can not instantiateItem for $position in $imageViewList")
        }

        private fun loadImage(view: View, position: Int) {
            val pbProgress = view.findViewById<ProgressBar>(R.id.pb_hor_progress)
            val pvImage = view.findViewById<ImageView>(R.id.pv_image_large)
            pbProgress?.animate()?.alpha(1f)?.setDuration(300)?.start()
            val offset = Math.abs(vpLargeImage.currentItem - position)
            val priority = when (offset) {
                in 0..1 -> Priority.IMMEDIATE
                in 2..5 -> Priority.HIGH
                in 6..10 -> Priority.NORMAL
                else -> Priority.LOW
            }
            val url = urls[position]
            Glide.with(this@WatchLargeImageActivity)
                .load(url.toGlideNoHostUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .error(R.drawable.ic_image_error)
                .fitCenter()
                .priority(priority)
                .into(object : DrawableImageViewTarget(pvImage) {
                    override fun onLoadStarted(placeholder: Drawable?) {
                        pbProgress?.animate()?.alpha(1f)?.setDuration(300)?.start()
                        super.onLoadStarted(placeholder)
                    }

                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        super.onResourceReady(resource, transition)
                        pbProgress?.animate()?.alpha(0f)?.setDuration(300)?.start()
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        pbProgress?.animate()?.alpha(0f)?.setDuration(300)?.start()
                    }
                })
        }

        override fun getCount() = imageViewList.size
        override fun isViewFromObject(arg0: View, arg1: Any) = arg0 === arg1
    }
}
