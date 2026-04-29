package me.jbusdriver.modern.data.db.service

import me.jbusdriver.modern.data.db.DB
import me.jbusdriver.modern.data.db.entity.Category

object CategoryService {

    fun getById(id: Int): Category? = DB.categoryDao.findById(id)

    fun insert(category: Category): Long = DB.categoryDao.insert(category)

    fun update(category: Category): Int = DB.categoryDao.update(category)

    fun delete(id: Int): Int = DB.categoryDao.delete(id)
}
