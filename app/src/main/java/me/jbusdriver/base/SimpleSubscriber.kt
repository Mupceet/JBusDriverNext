package me.jbusdriver.base

import io.reactivex.rxjava3.subscribers.DisposableSubscriber
import me.jbusdriver.common.KLog
import retrofit2.HttpException

open class SimpleSubscriber<T> : DisposableSubscriber<T>() {

    private val TAG: String = this.javaClass.name

    override fun onComplete() {
        cancel()
    }

    override fun onError(e: Throwable) {
        e.printStackTrace()
        KLog.t(TAG).e("onError >> info : ${e.message}")
        if (e is HttpException) {
            when (e.code()) {
                404 -> toast("没有结果")
            }
        }
        cancel()
    }

    override fun onNext(t: T) {
    }
}
