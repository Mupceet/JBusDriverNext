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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJBusDatabase(): JBusDatabase = me.jbusdriver.modern.data.db.DB.jBusDatabase

    @Provides
    @Singleton
    fun provideCollectDatabase(): CollectDatabase = me.jbusdriver.modern.data.db.DB.collectDatabase

    @Provides
    fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideCategoryDao(db: CollectDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLinkItemDao(db: CollectDatabase): LinkItemDao = db.linkItemDao()
}
