package me.jbusdriver.modern.data.magnet.loaders

import me.jbusdriver.modern.data.magnet.IMagnetLoader
import me.jbusdriver.modern.data.magnet.loaders.MagnetLoaders.Loaders

/**
 * 磁力加载器注册表，维护所有可用的磁力搜索站点实现。
 *
 * 职责：以名称为键、[IMagnetLoader] 实现为值，集中管理所有磁力加载器的映射关系。
 *
 * 使用场景：[MagnetManager] 通过 [Loaders] 查找和调用具体的磁力加载器实现。
 *
 * 线程：[Loaders] 通过 lazy 委迟初始化，线程安全。加载器实例本身应为无状态或线程安全的。
 */
object MagnetLoaders {
    /**
     * 已注册的磁力加载器映射表。
     * 当前仅包含 "default" 加载器（[DefaultLoaderImpl]），
     * 其他站点的加载器已注释保留，可按需启用。
     */
    val Loaders: Map<String, IMagnetLoader> by lazy {
        mapOf(
            "default" to DefaultLoaderImpl()
//            "超人" to ChaoRenLoaderImpl(),
//            "Btanv" to BtAntMagnetLoaderImpl(),
//            "Kitty" to CNBtkittyMangetLoaderImpl(),
//            "btdigg" to BtdiggsMagnetLoaderImpl(),
//            "zzjd" to ZZJDMagnetLoaderImpl(),
//            "BTbaocai" to BTBCMagnetLoaderImpl(),
//            "BTSO.PW" to BtsoPWMagnetLoaderImpl(),
//            "BTSOW" to BTSOWMagnetLoaderImpl(),
//            "BTDB" to BTDBMagnetLoaderImpl(),
//            "btcherries" to BTCherryMagnetLoaderImpl(),
//            "TorrentKitty" to TorrentKittyMangetLoaderImpl()
        )
    }

}
