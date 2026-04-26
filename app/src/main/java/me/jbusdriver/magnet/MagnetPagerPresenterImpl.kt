package me.jbusdriver.magnet

import me.jbusdriver.base.mvp.presenter.BasePresenterImpl

class MagnetPagerPresenterImpl : BasePresenterImpl<MagnetPagerContract.MagnetPagerView>(), MagnetPagerContract.MagnetPagerPresenter {
    override fun lazyLoad() {
        onFirstLoad()
    }
}
