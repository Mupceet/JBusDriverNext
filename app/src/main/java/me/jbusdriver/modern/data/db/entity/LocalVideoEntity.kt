package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "t_local_video", indices = [Index("code")])
data class LocalVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 命中的番号（大写归一化）。 */
    val code: String,
    /** 显示名（文件名，去路径）。 */
    val name: String,
    /** content:// 子文档 URI（凭 tree 持久权限可读）。 */
    val uri: String,
    val mime: String?,
    val size: Long,
    @ColumnInfo(name = "scannedAt") val scannedAt: Long,
)
