package me.jbusdriver.magnet

import me.jbusdriver.mvp.bean.ILink
import me.jbusdriver.mvp.bean.LinkCategory

data class Magnet(val name: String, val size: String, val date: String, override val link: String) : ILink {
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}
