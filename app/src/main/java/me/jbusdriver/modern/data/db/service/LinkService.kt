package me.jbusdriver.modern.data.db.service

import io.reactivex.rxjava3.core.Flowable
import me.jbusdriver.modern.data.db.DB
import me.jbusdriver.modern.data.db.entity.LinkItem

object LinkService {

    fun queryAll(): Flowable<List<LinkItem>> = DB.linkDao.listAll()

    fun saveOrUpdate(link: LinkItem) {
        val exists = DB.linkDao.hasByKey(link.dbType, link.key) > 0
        if (exists) {
            DB.linkDao.update(link)
        } else {
            DB.linkDao.insert(link)
        }
    }
}
