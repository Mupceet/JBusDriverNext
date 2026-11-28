package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 论坛账号流程的一次性信号（单例跨界面传递）。
 *
 * 内置浏览器是应用内叠加层，返回时不触发任何生命周期回调，无法靠
 * LifecycleResumeEffect 感知"网页登录结束"；由浏览器路由退出组合时
 * 发 [webLoginClosed]，论坛 ViewModel 收到后自动校准登录态。
 */
@Singleton
class ForumAccountSignals @Inject constructor() {
    private val _webLoginClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val webLoginClosed: SharedFlow<Unit> = _webLoginClosed.asSharedFlow()

    fun notifyWebLoginClosed() {
        _webLoginClosed.tryEmit(Unit)
    }
}
