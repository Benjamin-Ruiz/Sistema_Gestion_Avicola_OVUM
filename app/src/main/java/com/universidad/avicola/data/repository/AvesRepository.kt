package com.universidad.avicola.data.repository

import android.content.Context
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.local.entities.LoteEntity
import com.universidad.avicola.data.local.entities.RegistroDiarioEntity
import com.universidad.avicola.data.model.Lote
import com.universidad.avicola.data.model.RegistroDiarioAves
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * AvesRepository.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
 *
 * INTEGRACIÓN AÑADIDA: Aves → Finanzas
 * Cuando se registran bajas por mortalidad, se calcula la pérdida
 * económica (bajas × costo promedio por ave del lote) y se registra
 * automáticamente en el módulo de Finanzas.
 *
 * El costo promedio por ave se estima desde las estimaciones de costos
 * activas del lote. Si no hay estimación, se omite el registro en Finanzas
 * sin interrumpir el flujo principal.
 */
class AvesRepository(context: Context) {

    private val app            = context.applicationContext as AvicolaApp
    private val dao            = app.database.loteDao()
    private val finanzasRepo   = FinanzasRepository()
    private val costosDao      = app.database.estimacionCostosDao()

    fun getLotesActivos(): Flow<List<Lote>> =
        dao.getLotesActivos().map { entities -> entities.map { it.toDomain() } }

    suspend fun crearLote(lote: Lote) {
        dao.insertLote(LoteEntity.fromDomain(lote))
    }

    /**
     * Registra bajas (mortalidad + descarte) y pesaje diario.
     *
     * INTEGRACIÓN FINANZAS: si mortalidad > 0 y existe una estimación
     * activa para este lote, registra la pérdida económica en Finanzas.
     */
    suspend fun registrarBajasYPesaje(
        loteId: String,
        mortalidad: Int,
        descarte: Int,
        peso: Double,
        obs: String
    ) {
        // 1. Registrar en Room (comportamiento original intacto)
        dao.registrarMortalidad(loteId, mortalidad, descarte, peso, obs)

        // 2. INTEGRACIÓN → FINANZAS: registrar pérdida económica si hay bajas
        if (mortalidad > 0) {
            registrarPerdidaEnFinanzas(loteId, mortalidad)
        }
    }

    /**
     * Calcula la pérdida por mortalidad y la envía a Finanzas.
     * Usa el costo por ave de la estimación activa del lote.
     * Si no existe estimación, usa un costo base de Q0 (la pérdida
     * queda registrada con monto 0 para al menos tener el historial).
     */
    private suspend fun registrarPerdidaEnFinanzas(loteId: String, bajas: Int) {
        try {
            // Buscar si hay una estimación activa o completada para este lote
            val estimaciones = costosDao.getPorLoteSnapshot(loteId)
            val estimacionActiva = estimaciones
                .filter { it.estado == "ACTIVA" || it.estado == "COMPLETADA" }
                .maxByOrNull { it.fechaCreacion }

            val costoPorAve = estimacionActiva?.costoPorAve ?: 0.0

            // Solo registrar si hay un costo real o si siempre queremos el historial
            finanzasRepo.registrarPerdidaMortalidad(
                galponId         = loteId,
                cantidadBajas    = bajas,
                costoPromedioAve = costoPorAve,
                motivo           = "Mortalidad registrada en lote"
            )
        } catch (e: Exception) {
            // El fallo en Finanzas NO debe interrumpir el registro de aves
        }
    }

    suspend fun cerrarLote(lote: Lote) {
        dao.updateLote(LoteEntity.fromDomain(lote.copy(estado = "CERRADO")))
    }

    suspend fun eliminarLote(id: String) {
        dao.deleteLoteById(id)
    }

    fun getHistorialLote(loteId: String): Flow<List<RegistroDiarioAves>> =
        dao.getRegistrosPorLote(loteId).map { entities -> entities.map { it.toDomain() } }
}