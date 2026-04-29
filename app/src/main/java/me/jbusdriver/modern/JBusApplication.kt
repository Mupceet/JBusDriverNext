package me.jbusdriver.modern

import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.AppContext
import me.jbusdriver.modern.data.SettingsRepository
import me.jbusdriver.modern.data.remote.JAVBusService
import okhttp3.Interceptor
import me.jbusdriver.modern.JBus
import javax.inject.Inject

@HiltAndroidApp
class JBusApplication : AppContext(), ImageLoaderFactory {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d("JBusApplication", "Hilt + legacy initialization complete")
        }
        appScope.launch {
            settingsRepository.fetchAnnounce()
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
