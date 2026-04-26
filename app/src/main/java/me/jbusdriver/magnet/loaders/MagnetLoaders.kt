package me.jbusdriver.magnet.loaders

import me.jbusdriver.magnet.IMagnetLoader

object MagnetLoaders {
    /**  "btso.pw" to BtsoPWMagnetLoaderImpl()
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
