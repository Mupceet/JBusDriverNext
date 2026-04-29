package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.reactivex.rxjava3.core.Observable
import me.jbusdriver.modern.data.db.entity.Category

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(category: Category): Long

    @Query("DELETE FROM t_category WHERE id = :id")
    fun delete(id: Int): Int

    @Query("SELECT * FROM t_category WHERE id = :id")
    fun findById(id: Int): Category?

    @Query("SELECT * FROM t_category WHERE tree LIKE :like ORDER BY sort_order DESC")
    fun queryTreeByLike(like: String): Observable<List<Category>>

    @Update
    fun update(category: Category): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(categories: List<Category>): List<Long>
}
