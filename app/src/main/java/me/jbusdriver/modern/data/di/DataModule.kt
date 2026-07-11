package me.jbusdriver.modern.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.DefaultCacheStore
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.core.http.AndroidWebViewFactory
import me.jbusdriver.modern.core.http.DefaultHtmlClient
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.http.WebViewFactory
import me.jbusdriver.modern.core.site.DefaultSiteConfig
import me.jbusdriver.modern.core.site.SitePreferenceSource
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.CollectTransactionRunner
import me.jbusdriver.modern.data.settings.CollectionUiPrefs
import me.jbusdriver.modern.data.gateway.AndroidCollectionDocumentGateway
import me.jbusdriver.modern.data.gateway.CollectionDocumentGateway
import me.jbusdriver.modern.data.repository.DefaultCollectRepository
import me.jbusdriver.modern.data.repository.DefaultForumRepository
import me.jbusdriver.modern.data.repository.DefaultMagnetRepository
import me.jbusdriver.modern.data.repository.DefaultMovieDetailRepository
import me.jbusdriver.modern.data.repository.DefaultMovieRepository
import me.jbusdriver.modern.data.settings.DefaultSearchHistoryStore
import me.jbusdriver.modern.data.repository.DefaultSearchRepository
import me.jbusdriver.modern.core.http.BrowserCookiePersister
import me.jbusdriver.modern.core.http.BrowserSessionManager
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.data.settings.AppSettingsContract
import me.jbusdriver.modern.data.settings.AppSettingsStore
import me.jbusdriver.modern.data.settings.ForumSettingsReader
import me.jbusdriver.modern.data.settings.MovieListSettings
import me.jbusdriver.modern.data.settings.ThemeSettingsReader
import me.jbusdriver.modern.data.session.GifLoadTracker
import me.jbusdriver.modern.data.gateway.AndroidImageMediaGateway
import me.jbusdriver.modern.data.gateway.ImageMediaGateway
import me.jbusdriver.modern.data.localvideo.DocumentFileVideoFileDeleter
import me.jbusdriver.modern.data.localvideo.DocumentFileVideoFileSource
import me.jbusdriver.modern.data.localvideo.LocalVideoFileDeleter
import me.jbusdriver.modern.data.localvideo.LocalVideoFileSource
import me.jbusdriver.modern.data.session.LoadedGifTracker
import me.jbusdriver.modern.data.repository.DefaultLocalVideoRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.MagnetRepository
import me.jbusdriver.modern.data.repository.MovieDetailRepository
import me.jbusdriver.modern.data.repository.MovieRepository
import me.jbusdriver.modern.data.repository.RoomCollectTransactionRunner
import me.jbusdriver.modern.data.settings.SearchHistoryStore
import me.jbusdriver.modern.data.settings.DefaultThemeRepository
import me.jbusdriver.modern.data.settings.ThemeRepository
import me.jbusdriver.modern.data.repository.SearchRepository
import me.jbusdriver.modern.data.settings.UiPrefsStore
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
    abstract fun bindSitePreferenceSource(impl: AppSettingsStore): SitePreferenceSource

    @Binds
    @Singleton
    abstract fun bindHtmlClient(impl: DefaultHtmlClient): HtmlClient

    @Binds
    @Singleton
    abstract fun bindCacheStore(impl: DefaultCacheStore): CacheStore

    @Binds
    @Singleton
    abstract fun bindWebViewFactory(impl: AndroidWebViewFactory): WebViewFactory

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

    @Binds
    @Singleton
    abstract fun bindCollectionDocumentGateway(
        impl: AndroidCollectionDocumentGateway
    ): CollectionDocumentGateway

    @Binds
    @Singleton
    abstract fun bindImageMediaGateway(
        impl: AndroidImageMediaGateway
    ): ImageMediaGateway

    /** 绑定 [CollectRepository] 到 [DefaultCollectRepository]。 */
    @Binds
    @Singleton
    abstract fun bindCollectTransactionRunner(
        impl: RoomCollectTransactionRunner
    ): CollectTransactionRunner

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
    abstract fun bindBrowserSessionClient(
        impl: BrowserSessionManager
    ): BrowserSessionClient

    @Binds
    @Singleton
    abstract fun bindBrowserCookiePersister(
        impl: BrowserSessionManager
    ): BrowserCookiePersister

    @Binds
    @Singleton
    abstract fun bindMagnetRepository(
        impl: DefaultMagnetRepository
    ): MagnetRepository

    @Binds
    @Singleton
    abstract fun bindForumSettingsReader(
        impl: AppSettingsStore
    ): ForumSettingsReader

    @Binds
    @Singleton
    abstract fun bindThemeSettingsReader(impl: AppSettingsStore): ThemeSettingsReader

    @Binds
    @Singleton
    abstract fun bindMovieListSettings(impl: AppSettingsStore): MovieListSettings

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: DefaultThemeRepository): ThemeRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsContract(impl: AppSettingsStore): AppSettingsContract

    @Binds
    @Singleton
    abstract fun bindLoadedGifTracker(
        impl: GifLoadTracker
    ): LoadedGifTracker

    @Binds
    @Singleton
    abstract fun bindLocalVideoRepository(impl: DefaultLocalVideoRepository): LocalVideoRepository

    @Binds
    @Singleton
    abstract fun bindLocalVideoFileSource(impl: DocumentFileVideoFileSource): LocalVideoFileSource

    @Binds
    @Singleton
    abstract fun bindLocalVideoFileDeleter(impl: DocumentFileVideoFileDeleter): LocalVideoFileDeleter
}
