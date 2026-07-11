package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable

/** 一条本地视频关联记录（一个番号可有多条，对应多版本/多格式）。 */
@Immutable
data class LocalVideo(
    val code: String,
    val name: String,
    val uri: String,
    val mime: String?,
    val size: Long,
    val id: Int = 0,
    val title: String? = null,
    val imageUrl: String? = null,
    val date: String? = null,
    val censorType: String? = null,
)

/** 按番号分组的本地视频（供收藏页未收藏分区使用）。 */
data class LocalVideoGroup(
    val code: String,
    val title: String?,
    val imageUrl: String?,
    val date: String?,
    val censorType: String?,
    val files: List<LocalVideo>,
)

/** 本地视频功能在设置页的汇总展示。 */
data class LocalVideoSummary(
    val linkedCount: Int = 0,
    val lastScannedAt: Long? = null,
    val folderDisplayName: String? = null,
)

/** 批量删除结果。 */
data class DeleteResult(val deleted: Int, val failed: Int)
