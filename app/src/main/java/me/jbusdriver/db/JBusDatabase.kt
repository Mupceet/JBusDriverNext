package me.jbusdriver.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.db.dao.HistoryDao
import me.jbusdriver.db.entity.History

@Database(entities = [History::class], version = 1, exportSchema = true)
abstract class JBusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
