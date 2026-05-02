package me.jbusdriver.modern.data.remote.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.remote.JAVBusService
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt 网络依赖提供模块，负责创建和注入网络相关的单例对象。
 *
 * 职责：将 [OkHttpClient]、[Gson]、[JAVBusService] 注册到 Hilt 依赖图中，
 * 使 Repository 可以通过 `@Inject` 获取网络访问能力。
 *
 * 使用场景：所有需要网络请求的 Repository 实现类通过 Hilt 注入 [JAVBusService]。
 *
 * 线程：所有提供方法在应用生命周期内仅调用一次（@Singleton），线程安全由 Hilt 保证。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 提供全局共享的 OkHttp 客户端。
     * 使用 [NetClient.glideOkHttpClient] 以复用 Glide 的 OkHttpClient 配置。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetClient.glideOkHttpClient

    /** 提供全局 Gson 实例，使用项目统一配置的 [GSON]。 */
    @Provides
    @Singleton
    fun provideGson(): Gson = GSON

    /**
     * 提供默认的 JAVBus Retrofit 服务实例。
     * 使用 [JAVBusService.defaultFastUrl] 作为初始基地址，
     * 并缓存在 [JBus.JBusServices] 映射表中以便后续复用。
     */
    @Provides
    @Singleton
    fun provideJavBusService(): JAVBusService {
        return JBus.JBusServices.getOrPut(JAVBusService.defaultFastUrl) {
            JAVBusService.getInstance(JAVBusService.defaultFastUrl)
        }
    }
}
