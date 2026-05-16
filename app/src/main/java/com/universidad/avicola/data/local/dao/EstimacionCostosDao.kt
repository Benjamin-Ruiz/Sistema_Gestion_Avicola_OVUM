package com.universidad.avicola.data.local.dao

import androidx.room.*
import com.universidad.avicola.data.local.entities.EstimacionCostosEntity
import kotlinx.coroutines.flow.Flow

/**
 * EstimacionCostosDao.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/local/dao/
 *
 * AÑADIDO: getPorLoteSnapshot() — versión suspend (sin Flow) de getPorLote.
 * Se usa en AvesRepository para consultar estimaciones activas del lote
 * en el momento de registrar mortalidad, sin necesitar colectar un Flow.
 */
@Dao
interface EstimacionCostosDao {

    @Query("SELECT * FROM estimaciones_costos ORDER BY fechaCreacion DESC")
    fun getAllFlow(): Flow<List<EstimacionCostosEntity>>

    @Query("SELECT * FROM estimaciones_costos WHERE id = :id")
    suspend fun getById(id: String): EstimacionCostosEntity?

    @Query("SELECT * FROM estimaciones_costos WHERE loteId = :loteId ORDER BY fechaCreacion DESC")
    fun getPorLote(loteId: String): Flow<List<EstimacionCostosEntity>>

    /**
     * NUEVO: versión suspend para consultas puntuales (no reactivas).
     * Usada por AvesRepository al registrar mortalidad para calcular
     * la pérdida económica real basada en la estimación activa del lote.
     */
    @Query("SELECT * FROM estimaciones_costos WHERE loteId = :loteId ORDER BY fechaCreacion DESC")
    suspend fun getPorLoteSnapshot(loteId: String): List<EstimacionCostosEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(estimacion: EstimacionCostosEntity)

    @Update
    suspend fun update(estimacion: EstimacionCostosEntity)

    @Query("DELETE FROM estimaciones_costos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM estimaciones_costos")
    suspend fun count(): Int
}
