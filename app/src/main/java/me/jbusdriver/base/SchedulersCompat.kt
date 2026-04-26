package me.jbusdriver.base

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.FlowableTransformer
import io.reactivex.rxjava3.schedulers.Schedulers

object SchedulersCompat {
    @JvmStatic
    fun <T : Any> computation(): FlowableTransformer<T, T> =
        FlowableTransformer {
            it.subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread())
                .unsubscribeOn(Schedulers.single())
        }

    @JvmStatic
    fun <T : Any> io(): FlowableTransformer<T, T> =
        FlowableTransformer {
            it.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).unsubscribeOn(Schedulers.single())
        }

    @JvmStatic
    fun <T : Any> single(): FlowableTransformer<T, T> =
        FlowableTransformer {
            it.subscribeOn(Schedulers.single()).observeOn(AndroidSchedulers.mainThread())
                .unsubscribeOn(Schedulers.single())
        }

    @JvmStatic
    fun <T : Any> newThread(): FlowableTransformer<T, T> =
        FlowableTransformer {
            it.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                .unsubscribeOn(Schedulers.single())
        }

    @JvmStatic
    fun <T : Any> trampoline(): FlowableTransformer<T, T> =
        FlowableTransformer {
            it.subscribeOn(Schedulers.trampoline()).observeOn(AndroidSchedulers.mainThread())
                .unsubscribeOn(Schedulers.single())
        }

    @JvmStatic
    fun <T : Any> mainThread(): FlowableTransformer<T, T> =
        FlowableTransformer { it.observeOn(AndroidSchedulers.mainThread()).unsubscribeOn(Schedulers.single()) }
}
