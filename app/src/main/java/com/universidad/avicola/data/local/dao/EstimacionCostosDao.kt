package com.universidad.avicola.data.local.dao

import androidx.room.*
import com.universidad.avicola.data.local.entities.EstimacionCostosEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EstimacionCostosDao {

    @Query("SELECT * FROM estimaciones_costos ORDER BY fechaCreacion DESC")
    fun getAllFlow(): Flow<List<EstimacionCostosEntity>>

    @Query("SELECT * FROM estimaciones_costos WHERE id = :id")
    suspend fun getById(id: String): EstimacionCostosEntity?

    @Query("SELECT * FROM estimaciones_costos WHERE loteId = :loteId ORDER BY fechaCreacion DESC")
    fun getPorLote(loteId: String): Flow<List<EstimacionCostosEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(estimacion: EstimacionCostosEntity)

    @Update
    suspend fun update(estimacion: EstimacionCostosEntity)

    @Query("DELETE FROM estimaciones_costos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM estimaciones_costos")
    suspend fun count(): Int
}
