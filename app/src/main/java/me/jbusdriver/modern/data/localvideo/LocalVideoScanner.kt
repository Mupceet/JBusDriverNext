package me.jbusdriver.modern.data.localvideo

import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

/**
 * 把扫描到的文件列表按番号映射为索引实体（纯函数，无 Android 依赖）。
 *
 * 每个文件的番号按"文件夹优先、文件名兜底"确定：从直接父文件夹向外遍历祖先子文件夹名，
 * 首个能提取出番号的文件夹胜出；若无文件夹含番号，则回退到文件名。无番号的文件被丢弃。
 * 同一番号的多个文件各自保留一条（供详情页弹选择表）。
 */
fun scanVideoFiles(files: List<ScannedFile>, scannedAt: Long): List<LocalVideoEntity> =
    files.mapNotNull { f ->
        val code = f.parentFolderNames.firstNotNullOfOrNull { VideoCodeMatcher.extractCode(it) }
            ?: VideoCodeMatcher.extractCode(f.name)
        code?.let {
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
