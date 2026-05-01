package me.jbusdriver.modern.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 职责：Activity 生命周期管理器，追踪当前存活的 Activity 并提供全局 Context
 *
 * 使用场景：
 * - 在 AppContext.onCreate() 中注册生命周期回调
 * - 非 Activity/Fragment 场景下获取 Context（如 CacheLoader、NetClient）
 * - 获取当前栈顶 Activity
 *
 * 线程：manager 列表非线程安全，仅在主线程（Activity 生命周期回调）中操作
 */
object JBusManager : Application.ActivityLifecycleCallbacks {

    /** 存活的 Activity 引用列表，使用 WeakReference 避免内存泄漏 */
    val manager = mutableListOf<WeakReference<Activity>>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        manager.add(WeakReference(activity))
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
        manager.removeAll { it.get() == activity }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    /**
     * 获取全局可用 Context
     *
     * 优先级：栈顶 Activity → Application 引用 → 抛异常
     * 使用场景：CacheLoader、NetClient 等非 UI 组件需要 Context 时
     */
    val context: Context
        get() = manager.firstOrNull()?.get() as? Context
            ?: ref.get() ?: error("can't get context")

    private lateinit var ref: WeakReference<Context>

    /**
     * 初始化 Application 级 Context 引用
     *
     * @param app Application 实例，在 AppContext.onCreate() 中调用
     */
    fun setContext(app: Application) {
        this.ref = WeakReference(app)
    }
}
