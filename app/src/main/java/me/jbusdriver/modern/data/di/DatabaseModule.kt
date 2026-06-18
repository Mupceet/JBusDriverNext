package me.jbusdriver.modern.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.db.CollectDatabase
import me.jbusdriver.modern.data.db.JBusDatabase
import me.jbusdriver.modern.data.db.buildCollectDatabase
import me.jbusdriver.modern.data.db.buildJBusDatabase
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideJBusDatabase(@ApplicationContext context: Context): JBusDatabase =
        buildJBusDatabase(context)

    @Provides
    @Singleton
    fun provideCollectDatabase(@ApplicationContext context: Context): CollectDatabase =
        buildCollectDatabase(context)

    @Provides
    fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideCategoryDao(db: CollectDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLinkItemDao(db: CollectDatabase): LinkItemDao = db.linkItemDao()
}
