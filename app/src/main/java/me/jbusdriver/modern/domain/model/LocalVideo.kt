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
)

/** 本地视频功能在设置页的汇总展示。 */
data class LocalVideoSummary(
    val linkedCount: Int = 0,
    val lastScannedAt: Long? = null,
    val folderDisplayName: String? = null,
)
