package me.jbusdriver.modern.data

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.parser.parseMagnets
import me.jbusdriver.modern.domain.model.Magnet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

interface MagnetRepository {
    suspend fun fetchMagnets(gid: String, uc: String): List<Magnet>
}

@Singleton
class DefaultMagnetRepository @Inject constructor(
    private val htmlClient: HtmlClient,
    private val siteConfig: SiteConfig
) : MagnetRepository {

    override suspend fun fetchMagnets(gid: String, uc: String): List<Magnet> {
        val baseUrl = siteConfig.baseUrl
        val floor = Random.nextInt(1, 1001)
        val ajaxUrl = "$baseUrl/ajax/uncledatoolsbyajax.php?gid=$gid&lang=zh&uc=$uc&floor=$floor"

        KLog.d("Magnet: gid=$gid, uc=$uc, floor=$floor")
        val ajaxHtml = htmlClient.fetchHtml(ajaxUrl, showAll = true, referer = siteConfig.referer())
        KLog.d("Magnet: ajax response length=${ajaxHtml.length}")
        return parseMagnets(ajaxHtml)
    }
}
