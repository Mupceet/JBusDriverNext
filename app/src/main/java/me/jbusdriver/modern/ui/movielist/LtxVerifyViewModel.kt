package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.core.http.LtxWebViewSession
import me.jbusdriver.modern.data.repository.LtxRepository
import javax.inject.Inject

/**
 * LTX 人机验证浏览器页的会话中转（[RouteLtxVerify] 接线）。
 *
 * 验证浏览器与列表屏分属两个路由的 ViewModelStore，浏览器不能直接触达栈底
 * 的 [LtxListViewModel]；验证通过后把会话交给本 VM 注入 [LtxRepository]，
 * 仓库随即发射 `sessionAdopted` 事件，由列表 VM（挂在 RouteMain，压栈期间
 * 存活）订阅完成刷新。旋转重建后探测循环重入时本调用幂等（重复注入同会话）。
 */
@HiltViewModel
class LtxVerifyViewModel @Inject constructor(
    private val ltxRepository: LtxRepository,
) : ViewModel() {

    /** 验证浏览器探测通过：注入 Cookie 会话，使后续拉取走 OkHttp 快路径。 */
    fun onVerified(session: LtxWebSession) {
        ltxRepository.adoptWebSession(session)
    }

    /**
     * 验证浏览器页内握手完成（完全验证）：直接采纳加密会话，数据零协商直达，
     * 免去关闭后的 OkHttp 协商与隐形网关二次握手。
     */
    fun onCryptoSession(session: LtxWebViewSession) {
        ltxRepository.adoptCryptoSession(session)
    }
}
