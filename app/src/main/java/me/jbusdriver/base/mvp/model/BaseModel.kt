package me.jbusdriver.base.mvp.model

import io.reactivex.rxjava3.core.Flowable

/**
 * Created by Administrator on 2017/4/8.
 */
interface BaseModel<in T, R : Any> {
    fun requestFor(t: T): Flowable<R> = Flowable.empty()

    fun requestFromCache(t: T): Flowable<R>

}
