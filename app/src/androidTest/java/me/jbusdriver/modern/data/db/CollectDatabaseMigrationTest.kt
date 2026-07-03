package me.jbusdriver.modern.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 CollectDatabase 1→2 迁移：
 * - v1 唯一索引在 `key` 单列；v2 改为 `(dbType, key)` 复合唯一索引。
 * - 迁移后：同 key 跨 dbType 允许；同 (dbType, key) 仍被拒绝。
 *
 * Schema 文件由 Room Gradle 插件的 `schemaDirectory` 导出在 `app/schemas`，
 * MigrationTestHelper 会通过插件注入的 schema 定位自动发现。
 */
@RunWith(AndroidJUnit4::class)
class CollectDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CollectDatabase::class.java
    )

    private val dbName = "collect-migration-test.db"

    @Test
    fun migrate1To2_allows_same_key_across_dbType_and_enforces_composite_unique() {
        // 在 v1 库写入一行 (dbType=1, key="shared")
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO t_link (categoryId, dbType, createTime, key, jsonStr) " +
                    "VALUES (-1, 1, 0, 'shared', '{}')"
            )
            close()
        }

        // 运行真实迁移并校验结果 schema 与 v2 一致
        val db = helper.runMigrationsAndValidate(dbName, 2, true, COLLECT_MIGRATION_1_2)

        // v2 复合唯一：同 key、不同 dbType 现在允许
        db.execSQL(
            "INSERT INTO t_link (categoryId, dbType, createTime, key, jsonStr) " +
                "VALUES (-1, 2, 0, 'shared', '{}')"
        )

        // 同 (dbType, key) 仍被拒绝（绕过 DAO 的 IGNORE，直接 SQL 触发约束）
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO t_link (categoryId, dbType, createTime, key, jsonStr) " +
                    "VALUES (-1, 1, 0, 'shared', '{}')"
            )
        }

        db.close()
    }
}
