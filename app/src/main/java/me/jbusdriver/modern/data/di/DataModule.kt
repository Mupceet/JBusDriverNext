package me.jbusdriver.modern.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.DefaultCollectRepository
import me.jbusdriver.modern.data.DefaultMovieDetailRepository
import me.jbusdriver.modern.data.DefaultMovieRepository
import me.jbusdriver.modern.data.DefaultSearchRepository
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.SearchRepository
import javax.inject.Singleton

/**
 * Hilt 数据层绑定模块，将 Repository 接口绑定到其具体实现类。
 *
 * 职责：通过 `@Binds` 将 Repository 接口与默认实现类关联，
 * 使 ViewModel 和其他依赖方可以通过接口类型注入，实现依赖反转。
 *
 * 使用场景：ViewModel 通过 `@Inject constructor(repo: MovieRepository)` 获取 Repository 实例。
 *
 * 线程：所有绑定均为单例，线程安全由 Hilt 保证。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /** 绑定 [MovieRepository] 到 [DefaultMovieRepository]。 */
    @Binds
    @Singleton
    abstract fun bindMovieRepository(
        impl: DefaultMovieRepository
    ): MovieRepository

    /** 绑定 [MovieDetailRepository] 到 [DefaultMovieDetailRepository]。 */
    @Binds
    @Singleton
    abstract fun bindMovieDetailRepository(
        impl: DefaultMovieDetailRepository
    ): MovieDetailRepository

    /** 绑定 [SearchRepository] 到 [DefaultSearchRepository]。 */
    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: DefaultSearchRepository
    ): SearchRepository

    /** 绑定 [CollectRepository] 到 [DefaultCollectRepository]。 */
    @Binds
    @Singleton
    abstract fun bindCollectRepository(
        impl: DefaultCollectRepository
    ): CollectRepository
}
