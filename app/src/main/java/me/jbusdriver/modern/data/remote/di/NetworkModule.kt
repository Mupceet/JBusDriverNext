package me.jbusdriver.modern.data.remote.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.base.GSON
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.common.JBus
import me.jbusdriver.http.JAVBusService
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
