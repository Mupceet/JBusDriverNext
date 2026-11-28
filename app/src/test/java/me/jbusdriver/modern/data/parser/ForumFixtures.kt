package me.jbusdriver.modern.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets.UTF_8

/**
 * 目标论坛真实页面脱敏样本（app/src/test/resources/forum/fixtures）。
 *
 * 采集方式与脱敏规则见该目录 README.md；协议结论见 docs/FORUM_DESIGN.md 第二章。
 * 与手写 HTML 单测互补：这里固化真实站点的当前结构，模板改版时优先在这里爆出来。
 */
object ForumFixtures {
    const val SITE_BASE = "https://www.javbus.com"
    private const val FORUM_BASE_URI = "$SITE_BASE/forum/"

    fun html(name: String): String =
        ForumFixtures::class.java.getResourceAsStream("/forum/fixtures/$name")
            ?.readBytes()
            ?.toString(UTF_8)
            ?: error("forum fixture not found: $name")

    fun doc(name: String): Document = Jsoup.parse(html(name), FORUM_BASE_URI)
}
