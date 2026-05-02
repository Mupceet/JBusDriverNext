package me.jbusdriver.modern

import android.app.Application
import android.os.Environment
import android.util.Log
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.JBusManager
import java.io.File

/**
 * 全局 Application Context 引用，在 AppContext.onCreate() 中初始化
 *
 * 使用场景：非 Activity 组件（Repository、CacheLoader 等）通过 JBus 访问 Application Context
 */
lateinit var JBus: AppContext

/**
 * 职责：Application 基类，管理全局状态和生命周期
 *
 * 使用场景：JBusApplication 的父类，负责初始化 JBusManager、注册 Activity 生命周期回调
 * 线程：onCreate 在主线程
 *
 * 继承关系：JBusApplication → AppContext → Application
 */
open class AppContext : Application() {

    /** 调试模式检测：BuildConfig.DEBUG 或 SD 卡存在 debug 标记文件 */
    private val isDebug by lazy {
        BuildConfig.DEBUG || File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                    packageName
                    + File.separator + "debug"
        ).exists()
    }

    override fun onCreate() {
        super.onCreate()
        JBusManager.setContext(this)
        JBus = this

        if (isDebug) {
            Log.d("AppContext", "Debug mode enabled")
        }

        this.registerActivityLifecycleCallbacks(JBusManager)
    }

    /** 内存不足时释放资源 */
    override fun onLowMemory() {
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }
}
