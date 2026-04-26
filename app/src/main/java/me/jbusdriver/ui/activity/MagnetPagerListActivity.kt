package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.R
import me.jbusdriver.ui.fragment.MagnetPagersFragment

class MagnetPagerListActivity : BaseActivity() {

    private val keyword by lazy {
        intent.getStringExtra(C.BundleKey.Key_1) ?: error("must set keyword")
    }
    private val link by lazy { intent.getStringExtra(C.BundleKey.Key_2).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.comp_magnet_activity_magnet_list)
        val toolbar = findViewById<Toolbar>(R.id.comp_magnet_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(keyword)
        //go to SearchResultPagesFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.comp_magnet_fl_magnet_list, MagnetPagersFragment().apply {
                arguments = Bundle().apply {
                    putString(C.BundleKey.Key_1, keyword)
                    putString(C.BundleKey.Key_2, link)
                }
            }).commit()

    }

    private fun setTitle(title: String) {
        supportActionBar?.title = "$title 的磁力链接"
    }

    companion object {
        fun start(context: Context, keyword: String, link: String) {
            context.startActivity(
                Intent(context, MagnetPagerListActivity::class.java).apply {
                    putExtra(C.BundleKey.Key_1, keyword)
                    putExtra(C.BundleKey.Key_2, link)
                })
        }
    }
}
