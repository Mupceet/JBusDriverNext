package me.jbusdriver.common

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.PublishProcessor
import java.util.concurrent.ConcurrentHashMap

object RxBus {

    private val bus = PublishProcessor.create<Any>()
    private val stickyMap = ConcurrentHashMap<Class<*>, Any>()

    fun post(event: Any) {
        bus.onNext(event)
    }

    fun postSticky(event: Any) {
        stickyMap[event.javaClass] = event
        bus.onNext(event)
    }

    fun <T : Any> toFlowable(eventType: Class<T>): Flowable<T> =
        bus.ofType(eventType).onBackpressureDrop()

    @Suppress("UNCHECKED_CAST")
    fun <T> getStickyEvent(eventType: Class<T>): T? = stickyMap[eventType] as? T

    fun <T : Any> toFlowableSticky(eventType: Class<T>): Flowable<T> {
        val sticky = getStickyEvent(eventType)
        return if (sticky != null) {
            toFlowable(eventType).startWithItem(sticky)
        } else {
            toFlowable(eventType)
        }
    }

    fun removeStickyEvent(eventType: Class<*>) {
        stickyMap.remove(eventType)
    }
}
