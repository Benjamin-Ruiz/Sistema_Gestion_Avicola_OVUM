package com.universidad.avicola.data.local.dao

import androidx.room.*
import com.universidad.avicola.data.local.entities.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllFlow(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun getById(id: String): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(producto: ProductoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(productos: List<ProductoEntity>)

    @Update
    suspend fun update(producto: ProductoEntity)

    @Query("DELETE FROM productos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM productos WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ProductoEntity>

    @Query("UPDATE productos SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}
