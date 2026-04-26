package me.jbusdriver.common

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import me.jbusdriver.BuildConfig
import me.jbusdriver.base.JBusManager
import me.jbusdriver.base.arrayMapof
import me.jbusdriver.http.JAVBusService
import java.io.File


lateinit var JBus: AppContext


class AppContext : Application() {

    val JBusServices by lazy { arrayMapof<String, JAVBusService>() }
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

        RxJavaPlugins.setErrorHandler {
            Log.e("AppContext", "RxJava undeliverable error", it)
        }

        this.registerActivityLifecycleCallbacks(JBusManager)
    }


    override fun onLowMemory() {
        super.onLowMemory()
        JBusServices.clear()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        JBusServices.clear()
    }
}
