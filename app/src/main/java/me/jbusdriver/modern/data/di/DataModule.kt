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

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMovieRepository(
        impl: DefaultMovieRepository
    ): MovieRepository

    @Binds
    @Singleton
    abstract fun bindMovieDetailRepository(
        impl: DefaultMovieDetailRepository
    ): MovieDetailRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: DefaultSearchRepository
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindCollectRepository(
        impl: DefaultCollectRepository
    ): CollectRepository
}
