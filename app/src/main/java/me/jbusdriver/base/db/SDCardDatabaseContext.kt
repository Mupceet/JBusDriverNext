package me.jbusdriver.base.db

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import me.jbusdriver.common.KLog
import java.io.File
import java.io.IOException

abstract class SDCardDatabaseContext(base: Context) : ContextWrapper(base) {

    abstract val dir: String

    override fun getDatabasePath(name: String): File? {
        val sdExist = android.os.Environment.MEDIA_MOUNTED == android.os.Environment.getExternalStorageState()
        val parentDir = if (!sdExist) {
            KLog.e("SD卡不存在，请加载SD卡")
            filesDir.absolutePath
        } else {
            android.os.Environment.getExternalStorageDirectory().toString()
        }
        val dbDir = parentDir + File.separator + dir + File.separator
        val dbPath = dbDir + name
        val dirFile = File(dbDir)
        if (!dirFile.exists()) dirFile.mkdirs()

        var isFileCreateSuccess = false
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            val fileDb = File(filesDir.absolutePath + File.separator + dir + File.separator + name)
            try {
                if (sdExist && fileDb.exists() && fileDb.isFile) {
                    fileDb.copyTo(dbFile)
                    fileDb.delete()
                    isFileCreateSuccess = true
                } else {
                    isFileCreateSuccess = dbFile.createNewFile()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    if (!fileDb.exists()) {
                        fileDb.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        fileDb.createNewFile()
                    }
                } catch (e: Exception) {
                }
                return fileDb
            }
        } else isFileCreateSuccess = true

        return if (isFileCreateSuccess) dbFile else {
            KLog.w("无法创建数据库数据${dbFile.absolutePath}")
            null
        }
    }

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
        val dbPath = getDatabasePath(name) ?: return super.openOrCreateDatabase(name, mode, factory)
        return SQLiteDatabase.openOrCreateDatabase(dbPath.absolutePath, null)
    }

    override fun openOrCreateDatabase(
        name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase {
        val dbPath = getDatabasePath(name) ?: return super.openOrCreateDatabase(name, mode, factory, errorHandler)
        return SQLiteDatabase.openOrCreateDatabase(dbPath.absolutePath, null)
    }
}
