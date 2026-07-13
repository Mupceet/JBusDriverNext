package me.jbusdriver.modern.core.coroutine

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * 限定符：标记用于 IO 密集型工作（磁盘 / 数据库 / JSON 反序列化）的 [CoroutineDispatcher]。
 *
 * 引入可注入调度器的目的是把这些工作移出主线程；测试中可注入 [kotlinx.coroutines.test.TestDispatcher]
 * 以保证 [kotlinx.coroutines.test.runTest] 的确定性（真实 [Dispatchers.IO] 不受测试调度器驱动）。
 */
@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * 提供 [IoDispatcher] 调度器，默认为 [Dispatchers.IO]。
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
