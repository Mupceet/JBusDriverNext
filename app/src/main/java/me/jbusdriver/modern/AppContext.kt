package me.jbusdriver.modern

import android.app.Application
import android.os.Environment
import android.util.Log
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.JBusManager
import java.io.File

/**
 * 职责：Application 基类，管理全局状态和生命周期
 *
 * 使用场景：JBusApplication 的父类，负责注册 Activity 生命周期回调
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
