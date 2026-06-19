package me.jbusdriver.modern.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 职责：Activity 生命周期管理器，追踪当前存活的 Activity
 *
 * 使用场景：
 * - 在 AppContext.onCreate() 中注册生命周期回调
 * - 获取当前栈顶 Activity
 *
 * 线程：Activity 引用列表非线程安全，仅在主线程（Activity 生命周期回调）中操作
 */
object JBusManager : Application.ActivityLifecycleCallbacks {

    /** 存活的 Activity 引用列表，使用 WeakReference 避免内存泄漏 */
    private val activities = mutableListOf<WeakReference<Activity>>()

    val currentActivity: Activity?
        get() {
            pruneClearedReferences()
            return activities.lastOrNull()?.get()
        }

    val activeActivityCount: Int
        get() {
            pruneClearedReferences()
            return activities.size
        }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(WeakReference(activity))
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        // 释放已销毁 Activity 的弱引用，防止列表无限增长
        activities.removeAll { it.get() == activity || it.get() == null }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    private fun pruneClearedReferences() {
        activities.removeAll { it.get() == null }
    }
}
