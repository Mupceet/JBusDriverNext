package me.jbusdriver.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.entity.MultiItemEntity
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.common.JBus
import me.jbusdriver.common.KLog
import me.jbusdriver.common.RxBus
import me.jbusdriver.db.service.LinkService
import me.jbusdriver.mvp.bean.BackUpEvent
import me.jbusdriver.mvp.bean.Expand_Type_Head
import me.jbusdriver.mvp.bean.MenuOp
import me.jbusdriver.mvp.bean.MenuOpHead
import me.jbusdriver.ui.adapter.MenuOpAdapter
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.task.LoadCollectService
import java.io.File
import java.util.concurrent.TimeUnit
import me.jbusdriver.magnet.Configuration
import me.jbusdriver.magnet.MagnetManager
import com.afollestad.materialdialogs.list.listItemsMultiChoice

class SettingActivity : BaseActivity() {

    private var pageModeHolder = AppConfiguration.pageMode
    private val menuOpValue by lazy { AppConfiguration.menuConfig.toMutableMap() }
    private var pendingBackup = false

    private val backDir by lazy {
        val pathSuffix = File.separator + "collect" + File.separator + "backup" + File.separator
        val dir: String =
            createDir(Environment.getExternalStorageDirectory().absolutePath + File.separator + JBus.packageName + pathSuffix)
                ?: createDir(JBus.filesDir.absolutePath + pathSuffix)
                ?: error("cant not create collect dir in anywhere")
        File(dir)
    }

    private lateinit var tvCollectBackup: TextView
    private lateinit var llCollectBackupFiles: LinearLayout
    private lateinit var tvMagnetSource: TextView
    private lateinit var rvMenuOp: RecyclerView
    private lateinit var llPageModePage: LinearLayout
    private lateinit var llPageModeNormal: LinearLayout
    private lateinit var swCollectCategory: Switch
    private lateinit var llCheckPlugins: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        initViews()
        setToolBar()
        initSettingView()
        RxBus.toFlowable(BackUpEvent::class.java).throttleLast(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ event ->
                tvCollectBackup.text = "正在加载备份${event.path}的第${event.index}/${event.total}个"
                if (event.total == event.index) {
                    tvCollectBackup.text = "点击备份"
                    tvCollectBackup.isClickable = event.total == event.index
                }
            }, {
                tvCollectBackup.text = "点击备份"
                tvCollectBackup.isClickable = true
            }).addTo(rxManager)
    }

    private fun initViews() {
        tvCollectBackup = findViewById(R.id.tv_collect_backup)
        llCollectBackupFiles = findViewById(R.id.ll_collect_backup_files)
        tvMagnetSource = findViewById(R.id.tv_magnet_source)
        rvMenuOp = findViewById(R.id.rv_menu_op)
        llPageModePage = findViewById(R.id.ll_page_mode_page)
        llPageModeNormal = findViewById(R.id.ll_page_mode_normal)
        swCollectCategory = findViewById(R.id.sw_collect_category)
        llCheckPlugins = findViewById(R.id.ll_check_plugins)
    }

    @SuppressLint("ResourceAsColor")
    private fun initSettingView() {
        llCheckPlugins.setOnClickListener {
            MaterialDialog(this).show {
                title(text = "没有插件信息!")
            }
        }

        changePageMode(AppConfiguration.pageMode)
        llPageModePage.setOnClickListener {
            pageModeHolder = AppConfiguration.PageMode.Page
            changePageMode(AppConfiguration.PageMode.Page)
        }
        llPageModeNormal.setOnClickListener {
            pageModeHolder = AppConfiguration.PageMode.Normal
            changePageMode(AppConfiguration.PageMode.Normal)
        }

        val data: List<MultiItemEntity> = arrayListOf(
            MenuOpHead("个人").apply { MenuOp.mine.forEach { addSubItem(it) } },
            MenuOpHead("有碼").apply { MenuOp.nav_ma.forEach { addSubItem(it) } },
            MenuOpHead("無碼").apply { MenuOp.nav_uncensore.forEach { addSubItem(it) } },
            MenuOpHead("欧美").apply { MenuOp.nav_xyz.forEach { addSubItem(it) } },
            MenuOpHead("其他").apply { MenuOp.nav_other.forEach { addSubItem(it) } }
        )
        val adapter = MenuOpAdapter(data.toMutableList())
        rvMenuOp.adapter = adapter
        rvMenuOp.layoutManager = GridLayoutManager(viewContext, viewContext.spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.getItemViewType(position) == Expand_Type_Head) spanCount else 1
            }
        }

        val expandItems = data.filterIndexed { _, multiItemEntity ->
            multiItemEntity is MenuOpHead && multiItemEntity.allSubItem.any { it.isHow }
        }
        expandItems.forEach { head ->
            val pos = data.indexOf(head)
            (head as? MenuOpHead)?.let { menuHead ->
                if (!menuHead.isExpanded) {
                    menuHead.isExpanded = true
                    adapter.addData(pos + 1, menuHead.subItems.toMutableList())
                }
            }
        }
        adapter.setOnItemClickListener { _, view, position ->
            (adapter.data.getOrNull(position) as? MenuOp)?.let { op ->
                val cb = view.findViewById<Switch>(R.id.cb_nav_menu)
                cb?.let {
                    synchronized(it) {
                        it.isChecked = !it.isChecked
                        menuOpValue[op.name] = it.isChecked
                    }
                }
            }
        }

        loadMagNetConfig()

        swCollectCategory.isChecked = AppConfiguration.enableCategory
        swCollectCategory.setOnCheckedChangeListener { _, isChecked ->
            AppConfiguration.enableCategory = isChecked
        }

        tvCollectBackup.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                pendingBackup = true
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE
                )
            } else {
                doBackup()
            }
        }

        loadBackUp()
    }

    private fun doBackup() {
        val loading = MaterialDialog(viewContext).show { message(text = "正在备份...") }
        Flowable.fromCallable { backDir }
            .flatMap { file ->
                LinkService.queryAll().doOnNext { list ->
                    File(file, "backup${System.currentTimeMillis()}.json").writeText(list.toJsonString())
                }
            }.compose(SchedulersCompat.single())
            .doAfterTerminate { loading.dismiss() }
            .subscribeBy(onError = { toast("备份失败,请重新打开app") }, onNext = {
                toast("备份成功")
                loadBackUp()
            })
            .addTo(rxManager)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE && pendingBackup) {
            pendingBackup = false
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                toast("存储权限未授予，将备份至应用内部存储")
            }
            doBackup()
        }
    }

    private fun loadMagNetConfig() {
        val allKeys = MagnetManager.getLoaderKeys()
        tvMagnetSource.text = "已选 ${Configuration.getConfigKeys().size} 个磁力源"
        tvMagnetSource.setOnClickListener {
            val currentSelected = Configuration.getConfigKeys()
            val initialSelection = currentSelected
                .mapNotNull { allKeys.indexOf(it).takeIf { i -> i >= 0 } }
                .toIntArray()
            MaterialDialog(this).show {
                title(text = "选择磁力源")
                listItemsMultiChoice(
                    items = allKeys,
                    initialSelection = initialSelection
                ) { _: MaterialDialog, indices: IntArray, _: List<CharSequence> ->
                    val selected = indices.map { allKeys[it] }
                    Configuration.saveMagnetKeys(selected)
                    tvMagnetSource.text = "已选 ${selected.size} 个磁力源"
                }
                positiveButton(text = "确定")
                negativeButton(text = "取消")
            }
        }
    }

    private fun secondSpannableString(str: String): SpannableString {
        return SpannableString(str).apply {
            setSpan(RelativeSizeSpan(0.8f), 0, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(R.color.secondText.toColorInt()), 0, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showBackupFileInfo(file: File) {
        val tip = "拷贝备份目录或备份文件至其他手机的相同sd目录即可。如sdcard/me.jbusdriver/collect/backup/xxx.json"
        val str = SpannableStringBuilder("1. 路径")
            .append(System.getProperty("line.separator"))
            .append(secondSpannableString(file.absolutePath))
            .append(System.getProperty("line.separator"))
            .append(System.getProperty("line.separator"))
            .append("2. 迁移至其他手机")
            .append(System.getProperty("line.separator"))
            .append(secondSpannableString(tip))

        MaterialDialog(viewContext).show {
            title(text = "信息")
            message(text = str.toString())
        }
    }

    private fun loadBackUp() {
        llCollectBackupFiles.removeAllViews()
        Flowable.fromCallable { backDir }
            .map { dir ->
                val list = dir.walk().maxDepth(1).filter {
                    it.isFile && it.name.contains("backup.+json".toRegex())
                }.toList()
                if (list.isEmpty()) {
                    listOf(inflate(R.layout.layout_collect_back_edit_item).apply {
                        findViewById<TextView>(R.id.tv_backup_name).text = "没有备份呢~~"
                        findViewById<ImageView>(R.id.iv_backup_load).visibility = View.GONE
                        findViewById<ImageView>(R.id.iv_backup_delete).visibility = View.GONE
                        findViewById<ImageView>(R.id.iv_backup_info).setOnClickListener {
                            MaterialDialog(viewContext).show {
                                title(text = "信息")
                                message(text = "还没有备份哦？来一发试试！")
                            }
                        }
                    })
                } else {
                    list.mapIndexed { index, file ->
                        inflate(R.layout.layout_collect_back_edit_item).apply {
                            val date = DateUtils.formatDateTime(
                                viewContext, file.lastModified(),
                                DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
                            )

                            val tvName = findViewById<TextView>(R.id.tv_backup_name)
                            val ivInfo = findViewById<ImageView>(R.id.iv_backup_info)
                            val ivLoad = findViewById<ImageView>(R.id.iv_backup_load)
                            val ivDelete = findViewById<ImageView>(R.id.iv_backup_delete)

                            tvName.setOnClickListener { showBackupFileInfo(file) }
                            ivInfo.setOnClickListener { showBackupFileInfo(file) }

                            tvName.text = SpannableStringBuilder("${index + 1}. ${file.name}")
                                .append(System.getProperty("line.separator"))
                                .append("    ")
                                .append(secondSpannableString(date))

                            ivLoad.setOnClickListener {
                                MaterialDialog(viewContext).show {
                                    title(text = "加载备份")
                                    message(text = "${file.name}\n注意:相同文件会被覆盖")
                                    positiveButton(text = "确定") {
                                        LoadCollectService.startLoadBackUp(viewContext, file)
                                    }
                                    negativeButton(text = "取消")
                                }
                            }
                            ivDelete.setOnClickListener {
                                MaterialDialog(viewContext).show {
                                    title(text = "注意")
                                    message(text = "确定要删除${file.name}吗?")
                                    positiveButton(text = "确定") {
                                        file.deleteRecursively()
                                        loadBackUp()
                                    }
                                    negativeButton(text = "取消")
                                }
                            }
                        }
                    }
                }
            }.compose(SchedulersCompat.single())
            .subscribeBy { views ->
                views.forEach { llCollectBackupFiles.addView(it) }
            }
            .addTo(rxManager)
    }

    private fun changePageMode(mode: Int) {
        when (mode) {
            AppConfiguration.PageMode.Page -> {
                llPageModePage.setBackgroundResource(R.drawable.mode_page_shape_corner)
                llPageModeNormal.setBackgroundResource(0)
            }
            AppConfiguration.PageMode.Normal -> {
                llPageModePage.setBackgroundResource(0)
                llPageModeNormal.setBackgroundResource(R.drawable.mode_page_shape_corner)
            }
        }
    }

    private fun setToolBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        AppConfiguration.pageMode = pageModeHolder
        if (AppConfiguration.menuConfig != menuOpValue) AppConfiguration.saveSaveMenuConfig(menuOpValue)
    }

    companion object {
        private const val REQUEST_STORAGE = 100
        fun start(context: Context) = context.startActivity(Intent(context, SettingActivity::class.java))
    }
}
