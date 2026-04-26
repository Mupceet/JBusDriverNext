package me.jbusdriver.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import com.google.android.material.navigation.NavigationView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.AppBaseActivity
import me.jbusdriver.common.JBus
import me.jbusdriver.common.RxBus
import me.jbusdriver.mvp.MainContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.presenter.MainPresenterImpl
import java.util.concurrent.TimeUnit

class MainActivity : AppBaseActivity<MainContract.MainPresenter, MainContract.MainView>(),
    NavigationView.OnNavigationItemSelectedListener, MainContract.MainView {

    private val navigationView by lazy { findViewById<NavigationView>(R.id.nav_view) }
    private var selectMenu: MenuItem? = null

    // Header views
    private lateinit var tvAppVersion: TextView
    private lateinit var llGitUrl: LinearLayout
    private lateinit var llTelegram: LinearLayout
    private lateinit var llClickReload: LinearLayout
    private lateinit var tvAppSetting: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) intent.putExtras(savedInstanceState)
        bindRx()
        initNavigationView()
        initFragments()
    }

    private fun bindRx() {
        RxBus.toFlowable(MenuChangeEvent::class.java)
            .delay(100, TimeUnit.MILLISECONDS) //稍微延迟,否则设置可能没有完成
            .compose(SchedulersCompat.computation())
            .subscribeBy {
                val mayAdded = MenuOp.Ops.map { it.id.toString() }
                supportFragmentManager.fragments.filter { it.tag in mayAdded }.forEach {
                    supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
                }

                initFragments()
            }
            .addTo(rxManager)

        RxBus.toFlowable(CategoryChangeEvent::class.java)
            .debounce(500, TimeUnit.MILLISECONDS) //稍微延迟,否则设置可能没有完成
            .compose(SchedulersCompat.computation())
            .subscribeBy {
                supportFragmentManager.findFragmentByTag(R.id.mine_collect.toString())?.let {
                    supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
                }
                if (selectMenu?.itemId == R.id.mine_collect) setNavSelected()

            }.addTo(rxManager)

    }


    private fun initNavigationView() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()


        navigationView?.getHeaderView(0)?.apply {
            tvAppVersion = findViewById(R.id.tv_app_version)
            llGitUrl = findViewById(R.id.ll_git_url)
            llTelegram = findViewById(R.id.ll_telegram)
            llClickReload = findViewById(R.id.ll_click_reload)
            tvAppSetting = findViewById(R.id.tv_app_setting)

            tvAppVersion.text = packageInfo?.versionName ?: "未知版本"
            llGitUrl.setOnClickListener {
                browse("https://github.com/Ccixyj/JBusDriver")
            }
            llTelegram.setOnClickListener {
                browse("https://t.me/joinchat/HBJbEA-ka9TcWzaxjmD4hw")
            }
            llClickReload.setOnClickListener {
                CacheLoader.lru.evictAll()
                CacheLoader.acache.clear()
                JBus.JBusServices.clear()
                SplashActivity.start(this@MainActivity)
                finish()
            }

            tvAppSetting.setOnClickListener {
                SettingActivity.start(this@MainActivity)
                drawer.closeDrawer(GravityCompat.START)
            }


            fun tintTextLeftDrawable(parent: ViewGroup) {

                (0..parent.childCount).forEachIndexed { i, _ ->
                    //如果是容器,直接查子view
                    (parent.getChildAt(i) as? ViewGroup)?.let {
                        Schedulers.trampoline().scheduleDirect {
                            tintTextLeftDrawable(it)
                        }

                    } ?: (parent.getChildAt(i) as? TextView)?.compoundDrawables?.forEach {
                        if (it != null)
                            DrawableCompat.setTint(it, R.color.colorAccent.toColorInt())
                    }
                }
            }
            if (Build.VERSION.SDK_INT < 23 && this as? ViewGroup != null) {
                Schedulers.single().scheduleDirect {
                    Schedulers.trampoline().scheduleDirect {
                        tintTextLeftDrawable(this)
                    }.addTo(rxManager)
                }
            }


        }
        navigationView.setNavigationItemSelectedListener(this)

    }


    private fun initFragments() {

        MenuOp.Ops.forEach {
            navigationView.menu.findItem(it.id).isVisible = it.isHow
        }
        setNavSelected()
    }

    private fun setNavSelected() {
        val id = (MenuOp.Ops - MenuOp.mine).find { it.isHow }?.id
            ?: MenuOp.Ops.find { it.isHow }?.id ?: let {
                toast("至少配置一项菜单!!!!")
                return
            }
        val menuId = intent.getIntExtra("MenuSelectedItemId", id)
        val select = navigationView.menu.findItem(menuId)
        select?.let {
            navigationView.setCheckedItem(it.itemId)
            onNavigationItemSelected(it)
        }

    }


    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        switchFragment(item.itemId)
        //更新当前选择菜单
        selectMenu = item
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        supportActionBar?.title = selectMenu?.title
        return true
    }

    private fun switchFragment(itemId: Int) {
        val ft = supportFragmentManager.beginTransaction()

        val replace = supportFragmentManager.findFragmentByTag(itemId.toString()) ?: let {
            MenuOp.Ops.find { it.id == itemId }?.initializer?.invoke()?.apply {
                ft.add(R.id.content_main, this, itemId.toString())
            } ?: error("no matched fragment")
        }
        //如果id 与 selectMenu的id不一致则隐藏前一个选择菜单
        if (itemId != selectMenu?.itemId) {
            supportFragmentManager.findFragmentByTag(selectMenu?.itemId.toString())?.let {
                ft.hide(it)
            }
        }
        ft.show(replace)
        ft.commitAllowingStateLoss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        selectMenu?.let {
            outState.putInt("MenuSelectedItemId", it.itemId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun createPresenter() = MainPresenterImpl()

    override val layoutId = R.layout.activity_main


    companion object {
        fun start(current: Activity) {
            current.startActivity(Intent(current, MainActivity::class.java))
        }
    }
}
