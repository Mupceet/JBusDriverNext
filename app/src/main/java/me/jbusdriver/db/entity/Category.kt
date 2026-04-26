package me.jbusdriver.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "t_category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "p_id") val pId: Int = -1,
    val name: String,
    val tree: String,
    @ColumnInfo(name = "sort_order") val order: Int = 0
) {
    @delegate:Transient
    val depth: Int by lazy { tree.split("/").filter { it.isNotBlank() }.size }
}
