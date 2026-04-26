package me.jbusdriver.db.service

import me.jbusdriver.db.DB
import me.jbusdriver.db.entity.Category

object CategoryService {

    fun getById(id: Int): Category? = DB.categoryDao.findById(id)

    fun insert(category: Category): Long = DB.categoryDao.insert(category)

    fun update(category: Category): Int = DB.categoryDao.update(category)

    fun delete(id: Int): Int = DB.categoryDao.delete(id)
}
