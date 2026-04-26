package me.jbusdriver.ui.fragment

import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.schedulers.Schedulers
import me.jbusdriver.base.common.C
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
import me.jbusdriver.magnet.MagnetPagerContract.MagnetPagerPresenter
import me.jbusdriver.magnet.MagnetPagerContract.MagnetPagerView
import me.jbusdriver.magnet.MagnetPagerPresenterImpl
import me.jbusdriver.magnet.Configuration
import me.jbusdriver.magnet.MagnetManager

/**
 * Created by Administrator on 2017/7/17 0017.
 */
class MagnetPagersFragment : TabViewPagerFragment<MagnetPagerPresenter, MagnetPagerView>(),
    MagnetPagerView {

    private val keyword by lazy {
        arguments?.getString(C.BundleKey.Key_1) ?: error("must set keyword")
    }
    private val link by lazy { arguments?.getString(C.BundleKey.Key_2).orEmpty() }

    override fun createPresenter() = MagnetPagerPresenterImpl()

    override val mTitles: List<String> by lazy {
        val allKeys = MagnetManager.getLoaderKeys()
        Configuration.getConfigKeys().filter { allKeys.contains(it) }.toMutableList().apply {
            if (this.isEmpty()) {
                this.addAll(Configuration.getConfigKeys())
            }
            Schedulers.single().scheduleDirect {
                Configuration.saveMagnetKeys(this)
            }
        }


    }

    override val mFragments: List<Fragment> by lazy {
        mTitles.map {
            val mapKey = if ("default" == it) link else keyword
            MagnetListFragment.newInstance(mapKey, it)
        }
    }


}
