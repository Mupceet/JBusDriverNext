package me.jbusdriver.base.common

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.rxjava3.disposables.CompositeDisposable
import me.jbusdriver.base.JBusManager
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenter
import me.jbusdriver.base.postMain
import java.lang.ref.WeakReference

abstract class AppBaseFragment<P : BasePresenter<V>, V> : BaseFragment(), BaseView {

    private var mFirstStart: Boolean = false
    private var mViewReCreate = false
    protected var mBasePresenter: P? = null
    private var rootViewWeakRef: WeakReference<View>? = null
    private var isLazyLoaded = false

    protected var placeDialogHolder: MaterialDialog? = null

    protected abstract fun createPresenter(): P

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mFirstStart = savedInstanceState == null ||
                savedInstanceState.getBoolean(C.SavedInstanceState.RECREATION_SAVED_STATE, true)
        mBasePresenter = createPresenter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootViewWeakRef?.get()?.let {
            ((it.parent as? View) as? ViewGroup)?.also {
                it.removeView(rootViewWeakRef?.get())
            }
        } ?: run {
            if (!mFirstStart) mViewReCreate = true
            inflater.inflate(layoutId, container, false)?.let {
                rootViewWeakRef = WeakReference(it)
            }
        }
        return rootViewWeakRef?.get()
    }

    protected abstract val layoutId: Int

    protected abstract fun initWidget(rootView: View)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mFirstStart || mViewReCreate) {
            initWidget(rootViewWeakRef?.get() ?: error("view is no inflated!!"))
        }
    }

    private fun doStart() {
        Log.d(TAG, "$this doStart isFirst: $mFirstStart")
        requireNotNull(mBasePresenter)
        mBasePresenter?.onViewAttached(this as V)
        mBasePresenter?.onStart(mFirstStart)
        if (mFirstStart || mViewReCreate) {
            initData()
        }
        Log.d(TAG, "doStart lazyLoad $mFirstStart $isLazyLoaded $userVisibleHint")
        if ((mFirstStart || mViewReCreate) && !isLazyLoaded && userVisibleHint) {
            lazyLoad()
        }
        mFirstStart = false
        mViewReCreate = false
    }

    override fun onStart() {
        super.onStart()
        doStart()
    }

    override fun onResume() {
        super.onResume()
        mBasePresenter?.onResume()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        userVisibleHint = !hidden
    }

    @Suppress("DEPRECATION")
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (userVisibleHint) onVisible() else onInvisible()
    }

    protected open fun onVisible() {
        if (isLazyLoaded || mBasePresenter == null) {
        } else {
            lazyLoad()
        }
    }

    protected open fun lazyLoad() {
        if (isLazyLoaded) return
        if (mBasePresenter is BasePresenter.LazyLoaderPresenter)
            (mBasePresenter as? BasePresenter.LazyLoaderPresenter)?.lazyLoad()
        isLazyLoaded = true
    }

    protected open fun onInvisible() {}

    protected open fun initData() {}

    override fun onPause() {
        super.onPause()
        mBasePresenter?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mBasePresenter?.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBasePresenter?.onViewDetached()
    }

    override fun onDestroy() {
        super.onDestroy()
        rootViewWeakRef?.clear()
        rootViewWeakRef = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(C.SavedInstanceState.RECREATION_SAVED_STATE, mFirstStart)
    }

    override fun showLoading() {
        postMain {
            if (viewContext is Application) return@postMain
            placeDialogHolder = MaterialDialog(viewContext).show {
                message(text = "正在加载...")
            }
        }
    }

    override fun dismissLoading() {
        postMain {
            placeDialogHolder?.dismiss()
            placeDialogHolder = null
        }
    }

    protected open fun restoreState(bundle: Bundle) {
        mBasePresenter?.restoreFromState()
    }
}
