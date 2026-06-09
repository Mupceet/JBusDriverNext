package me.jbusdriver.modern.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.DefaultCacheStore
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.core.http.DefaultHtmlClient
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.DefaultSiteConfig
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.CollectionUiPrefs
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.DefaultCollectRepository
import me.jbusdriver.modern.data.DefaultForumSessionClient
import me.jbusdriver.modern.data.DefaultForumRepository
import me.jbusdriver.modern.data.DefaultMagnetRepository
import me.jbusdriver.modern.data.DefaultMovieDetailRepository
import me.jbusdriver.modern.data.DefaultMovieRepository
import me.jbusdriver.modern.data.DefaultSearchRepository
import me.jbusdriver.modern.data.DefaultSearchHistoryStore
import me.jbusdriver.modern.data.ForumSessionClient
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.data.MagnetRepository
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.SearchRepository
import me.jbusdriver.modern.data.SearchHistoryStore
import me.jbusdriver.modern.data.UiPrefsStore
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

    @Binds
    @Singleton
    abstract fun bindSiteConfig(impl: DefaultSiteConfig): SiteConfig

    @Binds
    @Singleton
    abstract fun bindHtmlClient(impl: DefaultHtmlClient): HtmlClient

    @Binds
    @Singleton
    abstract fun bindCacheStore(impl: DefaultCacheStore): CacheStore

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

    @Binds
    @Singleton
    abstract fun bindSearchHistoryStore(
        impl: DefaultSearchHistoryStore
    ): SearchHistoryStore

    @Binds
    @Singleton
    abstract fun bindCollectionUiPrefs(
        impl: UiPrefsStore
    ): CollectionUiPrefs

    /** 绑定 [CollectRepository] 到 [DefaultCollectRepository]。 */
    @Binds
    @Singleton
    abstract fun bindCollectRepository(
        impl: DefaultCollectRepository
    ): CollectRepository

    /** 绑定 [ForumRepository] 到 [DefaultForumRepository]。 */
    @Binds
    @Singleton
    abstract fun bindForumRepository(
        impl: DefaultForumRepository
    ): ForumRepository

    @Binds
    @Singleton
    abstract fun bindForumSessionClient(
        impl: DefaultForumSessionClient
    ): ForumSessionClient

    @Binds
    @Singleton
    abstract fun bindBrowserSessionClient(
        impl: DefaultForumSessionClient
    ): BrowserSessionClient

    @Binds
    @Singleton
    abstract fun bindMagnetRepository(
        impl: DefaultMagnetRepository
    ): MagnetRepository
}
