package me.jbusdriver.base.mvp.model

import io.reactivex.rxjava3.core.Flowable
import me.jbusdriver.base.addUserCase

abstract class AbstractBaseModel<in P, R : Any>(private val op: (P) -> Flowable<R>) : BaseModel<P, R> {
    override fun requestFor(t: P): Flowable<R> = op.invoke(t).addUserCase()
}
