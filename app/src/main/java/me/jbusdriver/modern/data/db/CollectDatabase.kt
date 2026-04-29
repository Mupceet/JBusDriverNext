package me.jbusdriver.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.entity.Category
import me.jbusdriver.modern.data.db.entity.LinkItem

@Database(entities = [Category::class, LinkItem::class], version = 1, exportSchema = true)
abstract class CollectDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun linkItemDao(): LinkItemDao
}
