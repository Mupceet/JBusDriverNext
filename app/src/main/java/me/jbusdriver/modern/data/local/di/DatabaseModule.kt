package me.jbusdriver.modern.data.local.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.db.CollectDatabase
import me.jbusdriver.db.JBusDatabase
import me.jbusdriver.db.dao.CategoryDao
import me.jbusdriver.db.dao.HistoryDao
import me.jbusdriver.db.dao.LinkItemDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJBusDatabase(): JBusDatabase = me.jbusdriver.db.DB.jBusDatabase

    @Provides
    @Singleton
    fun provideCollectDatabase(): CollectDatabase = me.jbusdriver.db.DB.collectDatabase

    @Provides
    fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideCategoryDao(db: CollectDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLinkItemDao(db: CollectDatabase): LinkItemDao = db.linkItemDao()
}
