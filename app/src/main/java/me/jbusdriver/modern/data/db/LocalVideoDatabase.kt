package me.jbusdriver.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.modern.data.db.dao.LocalVideoDao
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

@Database(entities = [LocalVideoEntity::class], version = 2, exportSchema = true)
abstract class LocalVideoDatabase : RoomDatabase() {
    abstract fun localVideoDao(): LocalVideoDao
}
