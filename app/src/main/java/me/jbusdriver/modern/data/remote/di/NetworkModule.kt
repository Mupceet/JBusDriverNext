package me.jbusdriver.modern.data.remote.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.data.remote.JAVBusService
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetClient.glideOkHttpClient

    @Provides
    @Singleton
    fun provideGson(): Gson = GSON

    @Provides
    @Singleton
    fun provideJavBusService(): JAVBusService {
        return JBus.JBusServices.getOrPut(JAVBusService.defaultFastUrl) {
            JAVBusService.getInstance(JAVBusService.defaultFastUrl)
        }
    }
}
