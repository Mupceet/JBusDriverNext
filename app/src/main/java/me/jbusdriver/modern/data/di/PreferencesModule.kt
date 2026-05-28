package me.jbusdriver.modern.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.AppPreferences
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
annotation class LabSettingsPrefs

@Qualifier
annotation class SearchHistoryPrefs

@Qualifier
annotation class SessionCookiePrefs

@Qualifier
annotation class UiPrefs

@Qualifier
annotation class GifPrefs

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    @LabSettingsPrefs
    fun provideLabSettingsPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.LAB_SETTINGS, 0)

    @Provides
    @Singleton
    @SearchHistoryPrefs
    fun provideSearchHistoryPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.SEARCH_HISTORY, 0)

    @Provides
    @Singleton
    @SessionCookiePrefs
    fun provideSessionCookiePrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.SESSION_COOKIES, 0)

    @Provides
    @Singleton
    @UiPrefs
    fun provideUiPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.UI_PREFS, 0)

    @Provides
    @Singleton
    @GifPrefs
    fun provideGifPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.GIF_LOADED_URLS, 0)
}
