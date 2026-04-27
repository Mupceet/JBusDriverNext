package me.jbusdriver.modern

import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import me.jbusdriver.BuildConfig
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.common.AppContext
import me.jbusdriver.http.JAVBusService
import okhttp3.Interceptor
import me.jbusdriver.common.JBus

@HiltAndroidApp
class JBusApplication : AppContext(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d("JBusApplication", "Hilt + legacy initialization complete")
        }
    }

    override fun newImageLoader(): ImageLoader {
        val imageClient = NetClient.glideOkHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Referer", "${JAVBusService.defaultFastUrl}/")
                    .build()
                chain.proceed(request)
            })
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .crossfade(true)
            .build()
    }
}
