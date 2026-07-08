package me.jbusdriver.modern.data.localvideo

import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

/**
 * 把扫描到的文件列表按番号映射为索引实体（纯函数，无 Android 依赖）。
 *
 * 无番号的文件被丢弃；同一番号的多个文件各自保留一条（供详情页弹选择表）。
 */
fun scanVideoFiles(files: List<ScannedFile>, scannedAt: Long): List<LocalVideoEntity> =
    files.mapNotNull { f ->
        VideoCodeMatcher.extractCode(f.name)?.let { code ->
            LocalVideoEntity(
                code = code,
                name = f.name,
                uri = f.uri,
                mime = f.mime,
                size = f.size,
                scannedAt = scannedAt,
            )
        }
    }
