package me.jbusdriver.base.ui.fragment

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseFragment
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenter

abstract class TabViewPagerFragment<P : BasePresenter<V>, V : BaseView> : AppBaseFragment<P, V>() {

    abstract val mTitles: List<String>
    abstract val mFragments: List<Fragment>

    override val layoutId = R.layout.base_layout_tab_view_pager

    protected lateinit var tabLayout: TabLayout
    protected lateinit var vpFragment: ViewPager

    override fun initWidget(rootView: View) {
        tabLayout = rootView.findViewById(R.id.tabLayout)
        vpFragment = rootView.findViewById(R.id.vp_fragment)
        initForViewPager()
    }

    protected fun initForViewPager() {
        mTitles.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        vpFragment.offscreenPageLimit = mTitles.size
        vpFragment.adapter = pagerAdapter
        tabLayout.setupWithViewPager(vpFragment)
        require(mTitles.size == mFragments.size)
        if (mTitles.size >= 5) {
            tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        }
    }

    private val pagerAdapter: FragmentPagerAdapter by lazy {
        require(mTitles.size == mFragments.size)
        object : FragmentPagerAdapter(childFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return if (mFragments.size > position) mFragments[position]
                else error("you must put fragment in mFragments and size equal mTitles")
            }

            override fun getCount(): Int = mTitles.size
            override fun getPageTitle(position: Int): CharSequence = mTitles[position]
        }
    }
}
