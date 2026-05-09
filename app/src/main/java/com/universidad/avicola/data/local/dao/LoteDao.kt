package com.universidad.avicola.data.local.dao

import androidx.room.*
import com.universidad.avicola.data.local.entities.LoteEntity
import com.universidad.avicola.data.local.entities.RegistroDiarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoteDao {
    @Query("SELECT * FROM lotes WHERE estado = 'ACTIVO' ORDER BY fechaIngreso DESC")
    fun getLotesActivos(): Flow<List<LoteEntity>>

    @Query("SELECT * FROM lotes ORDER BY fechaIngreso DESC")
    fun getAllLotes(): Flow<List<LoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLote(lote: LoteEntity)

    @Update
    suspend fun updateLote(lote: LoteEntity)

    @Query("SELECT * FROM registros_diarios_aves WHERE loteId = :loteId ORDER BY fecha DESC")
    fun getRegistrosPorLote(loteId: String): Flow<List<RegistroDiarioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistro(registro: RegistroDiarioEntity)

    @Transaction
    suspend fun registrarMortalidad(loteId: String, mortalidad: Int, descarte: Int, peso: Double, obs: String) {
        val lote = getLoteById(loteId) ?: return
        val nuevaCantidad = lote.cantidadActual - mortalidad - descarte
        
        updateLote(lote.copy(cantidadActual = nuevaCantidad))
        
        insertRegistro(RegistroDiarioEntity(
            id = java.util.UUID.randomUUID().toString(),
            loteId = loteId,
            fecha = System.currentTimeMillis(),
            mortalidad = mortalidad,
            descarte = descarte,
            pesoPromedio = peso,
            observaciones = obs
        ))
    }

    @Query("SELECT * FROM lotes WHERE id = :id")
    suspend fun getLoteById(id: String): LoteEntity?

    @Query("DELETE FROM lotes WHERE id = :id")
    suspend fun deleteLoteById(id: String)
}
