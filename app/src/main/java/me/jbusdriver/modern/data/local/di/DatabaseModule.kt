package me.jbusdriver.modern.data.local.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.db.CollectDatabase
import me.jbusdriver.modern.data.db.JBusDatabase
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import javax.inject.Singleton

/**
 * Hilt 数据库依赖提供模块，负责创建和提供 Room 数据库实例及 DAO。
 *
 * 职责：将 [JBusDatabase]、[CollectDatabase] 及其 DAO 注册到 Hilt 依赖图中，
 * 使 ViewModel 和 Repository 可以通过 `@Inject` 获取数据库访问对象。
 *
 * 使用场景：所有需要数据库访问的 Repository 实现类通过 Hilt 注入 DAO。
 *
 * 线程：所有提供方法在应用生命周期内仅调用一次（@Singleton），线程安全由 Hilt 保证。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供 JBus 主数据库（历史记录）单例。
     * 委托给 [me.jbusdriver.modern.data.db.DB.jBusDatabase] 进行延迟初始化。
     */
    @Provides
    @Singleton
    fun provideJBusDatabase(): JBusDatabase = me.jbusdriver.modern.data.db.DB.jBusDatabase

    /**
     * 提供收藏数据库单例。
     * 委托给 [me.jbusdriver.modern.data.db.DB.collectDatabase] 进行延迟初始化。
     */
    @Provides
    @Singleton
    fun provideCollectDatabase(): CollectDatabase = me.jbusdriver.modern.data.db.DB.collectDatabase

    /** 提供历史记录 DAO。 */
    @Provides
    fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()

    /** 提供分类 DAO。 */
    @Provides
    fun provideCategoryDao(db: CollectDatabase): CategoryDao = db.categoryDao()

    /** 提供收藏条目 DAO。 */
    @Provides
    fun provideLinkItemDao(db: CollectDatabase): LinkItemDao = db.linkItemDao()
}
