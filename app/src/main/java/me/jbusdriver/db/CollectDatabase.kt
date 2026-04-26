package me.jbusdriver.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.db.dao.CategoryDao
import me.jbusdriver.db.dao.LinkItemDao
import me.jbusdriver.db.entity.Category
import me.jbusdriver.db.entity.LinkItem

@Database(entities = [Category::class, LinkItem::class], version = 1, exportSchema = true)
abstract class CollectDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun linkItemDao(): LinkItemDao
}
