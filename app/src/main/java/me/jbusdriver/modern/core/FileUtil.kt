package me.jbusdriver.modern.core

import android.util.Log
import java.io.File

private const val TAG = "FileUtil"

/**
 * 安全创建目录
 *
 * @param collectDir 目标目录路径
 * @return 创建成功返回路径，失败返回 null
 */
fun createDir(collectDir: String): String? {
    File(collectDir.trim()).let {
        try {
            if (!it.exists() && it.mkdirs()) return collectDir
            if (it.exists()) {
                if (it.isDirectory) {
                    return collectDir
                } else {
                    // 同名文件存在时先删除再重建
                    it.delete()
                    createDir(collectDir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createDir error", e)
        }
    }
    return null
}
