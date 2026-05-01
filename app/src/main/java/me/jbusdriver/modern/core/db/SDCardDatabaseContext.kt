package me.jbusdriver.modern.core.db

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import me.jbusdriver.modern.KLog
import java.io.File
import java.io.IOException

/**
 * 职责：将 Room 数据库文件存储到 SD 卡指定目录，支持从内部存储自动迁移
 *
 * 使用场景：CollectDatabase 通过此类将收藏数据存储到 SD 卡，
 * 实现卸载重装后收藏数据不丢失
 *
 * 线程：数据库操作由 Room 管理，通常在后台线程
 *
 * @param base 基础 Context
 */
abstract class SDCardDatabaseContext(base: Context) : ContextWrapper(base) {

    /** 数据库文件存储的相对目录路径（相对于 SD 卡根目录） */
    abstract val dir: String

    /**
     * 根据数据库名称计算实际文件路径
     *
     * 优先使用 SD 卡路径；SD 卡不可用时降级到内部存储。
     * 首次运行时，自动将内部存储的旧数据库文件迁移到 SD 卡。
     */
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
            // 检查内部存储是否有旧数据库文件，有则迁移到 SD 卡
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
                // SD 卡写入失败时降级回内部存储
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
