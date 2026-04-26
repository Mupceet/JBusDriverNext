package me.jbusdriver.base.common

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import me.jbusdriver.base.JBusManager

/**
 * Created by Administrator on 2016/8/11 0011.
 */
open class BaseFragment : Fragment() {
    protected val TAG: String by lazy { this::class.java.simpleName }
    val rxManager by lazy { CompositeDisposable() }
    protected val tempSaveBundle by lazy { Bundle() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView")
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        rxManager.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        rxManager.clear()
        rxManager.dispose()
    }

    //region other
    val viewContext: Context
        get() = activity ?: JBusManager.context
    //endregion
}
