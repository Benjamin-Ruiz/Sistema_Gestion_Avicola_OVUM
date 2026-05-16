package com.universidad.avicola.data.local.dao

import androidx.room.*
import com.universidad.avicola.data.local.entities.RegistroMedicoEntity
import com.universidad.avicola.data.local.entities.VacunacionEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
//  SaludDao.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/data/local/dao/
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface RegistroMedicoDao {

    @Query("SELECT * FROM registros_medicos ORDER BY fechaMs DESC")
    fun getAllFlow(): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos WHERE loteId = :loteId ORDER BY fechaMs DESC")
    fun getPorLote(loteId: String): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos WHERE resuelta = 0 ORDER BY fechaMs DESC")
    fun getPendientes(): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos WHERE gravedad IN ('ALTA','CRITICA') AND resuelta = 0 ORDER BY fechaMs DESC")
    fun getUrgentes(): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos WHERE id = :id")
    suspend fun getById(id: String): RegistroMedicoEntity?

    @Query("SELECT COUNT(*) FROM registros_medicos WHERE loteId = :loteId AND resuelta = 0")
    suspend fun countPendientesPorLote(loteId: String): Int

    @Query("SELECT SUM(costo) FROM registros_medicos WHERE loteId = :loteId")
    suspend fun costoTotalPorLote(loteId: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(registro: RegistroMedicoEntity)

    @Update
    suspend fun update(registro: RegistroMedicoEntity)

    @Query("DELETE FROM registros_medicos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE registros_medicos SET resuelta = 1 WHERE id = :id")
    suspend fun marcarResuelta(id: String)
}

@Dao
interface VacunacionDao {

    @Query("SELECT * FROM vacunaciones ORDER BY fechaProximaMs ASC")
    fun getAllFlow(): Flow<List<VacunacionEntity>>

    @Query("SELECT * FROM vacunaciones WHERE loteId = :loteId ORDER BY fechaAplicacionMs DESC")
    fun getPorLote(loteId: String): Flow<List<VacunacionEntity>>

    @Query("SELECT * FROM vacunaciones WHERE aplicada = 0 ORDER BY fechaProximaMs ASC")
    fun getPendientes(): Flow<List<VacunacionEntity>>

    @Query("SELECT COUNT(*) FROM vacunaciones WHERE loteId = :loteId AND aplicada = 0")
    suspend fun countPendientesPorLote(loteId: String): Int

    @Query("SELECT SUM(costo) FROM vacunaciones WHERE loteId = :loteId")
    suspend fun costoTotalPorLote(loteId: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vacunacion: VacunacionEntity)

    @Update
    suspend fun update(vacunacion: VacunacionEntity)

    @Query("DELETE FROM vacunaciones WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vacunaciones SET aplicada = 1, fechaAplicacionMs = :fechaMs WHERE id = :id")
    suspend fun marcarAplicada(id: String, fechaMs: Long)
}
