package me.jbusdriver.base.common

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenter

abstract class AppBaseActivity<P : BasePresenter<V>, in V : BaseView> : BaseActivity(),
    BaseView {
    private var mFirstStart: Boolean = false
    protected var mBasePresenter: P? = null

    private var placeDialogHolder: MaterialDialog? = null

    protected abstract fun createPresenter(): P

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mFirstStart = savedInstanceState == null ||
                savedInstanceState.getBoolean(C.SavedInstanceState.RECREATION_SAVED_STATE, true)
        setContentView(this.inflate(layoutId))
        mBasePresenter = createPresenter()
        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        }
    }

    override fun onStart() {
        super.onStart()
        doStart()
    }

    protected open fun doStart() {
        Log.d(TAG, "$this doStart isFirst: $mFirstStart")
        requireNotNull(mBasePresenter)
        mBasePresenter?.onViewAttached(this as V)
        mBasePresenter?.onStart(mFirstStart)
        mFirstStart = false
    }

    override fun onResume() {
        super.onResume()
        mBasePresenter?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mBasePresenter?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mBasePresenter?.onStop()
    }

    override fun onDestroy() {
        mBasePresenter?.onViewDetached()
        super.onDestroy()
        rxManager.dispose()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(C.SavedInstanceState.RECREATION_SAVED_STATE, mFirstStart)
    }

    protected abstract val layoutId: Int

    override fun showLoading() {
        runOnUiThread {
            if (viewContext is Application) return@runOnUiThread
            placeDialogHolder = MaterialDialog(viewContext).show {
                message(text = "正在加载...")
            }
        }
    }

    override fun dismissLoading() {
        runOnUiThread {
            placeDialogHolder?.dismiss()
            placeDialogHolder = null
        }
    }

    protected open fun restoreState(bundle: Bundle) {
        mBasePresenter?.restoreFromState()
    }
}
