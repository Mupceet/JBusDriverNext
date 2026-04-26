package me.jbusdriver.modern

import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import me.jbusdriver.BuildConfig
import me.jbusdriver.common.AppContext
import me.jbusdriver.common.JBus

@HiltAndroidApp
class JBusApplication : AppContext() {

    override fun onCreate() {
        // AppContext.onCreate() handles: JBusManager, JBus assignment,
        // RxJavaPlugins error handler, and ActivityLifecycleCallbacks registration
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d("JBusApplication", "Hilt + legacy initialization complete")
        }
    }
}
