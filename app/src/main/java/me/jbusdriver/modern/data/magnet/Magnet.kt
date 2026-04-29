package me.jbusdriver.modern.data.magnet

import me.jbusdriver.modern.domain.model.ILink
import me.jbusdriver.modern.domain.model.LinkCategory

data class Magnet(val name: String, val size: String, val date: String, override val link: String) : ILink {
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}
