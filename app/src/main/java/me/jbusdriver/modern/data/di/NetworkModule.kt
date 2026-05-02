package me.jbusdriver.modern.data.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.http.NetClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt 网络依赖提供模块，负责创建和注入网络相关的单例对象。
 *
 * 职责：将 [OkHttpClient]、[Gson] 注册到 Hilt 依赖图中。
 *
 * 线程：所有提供方法在应用生命周期内仅调用一次（@Singleton），线程安全由 Hilt 保证。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 提供全局共享的 OkHttp 客户端。
     * 使用 [NetClient.glideOkHttpClient] 以复用 OkHttp 连接池和拦截器配置。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetClient.glideOkHttpClient

    /** 提供全局 Gson 实例，使用项目统一配置的 [GSON]。 */
    @Provides
    @Singleton
    fun provideGson(): Gson = GSON
}
