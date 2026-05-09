package com.universidad.avicola.data.repository

import android.content.Context
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.local.entities.LoteEntity
import com.universidad.avicola.data.local.entities.RegistroDiarioEntity
import com.universidad.avicola.data.model.Lote
import com.universidad.avicola.data.model.RegistroDiarioAves
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AvesRepository(context: Context) {
    private val app = context.applicationContext as AvicolaApp
    private val dao = app.database.loteDao()

    fun getLotesActivos(): Flow<List<Lote>> = dao.getLotesActivos().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun crearLote(lote: Lote) {
        dao.insertLote(LoteEntity.fromDomain(lote))
    }

    suspend fun registrarBajasYPesaje(loteId: String, mortalidad: Int, descarte: Int, peso: Double, obs: String) {
        dao.registrarMortalidad(loteId, mortalidad, descarte, peso, obs)
    }

    suspend fun cerrarLote(lote: Lote) {
        dao.updateLote(LoteEntity.fromDomain(lote.copy(estado = "CERRADO")))
    }

    suspend fun eliminarLote(id: String) {
        dao.deleteLoteById(id)
    }

    fun getHistorialLote(loteId: String): Flow<List<RegistroDiarioAves>> = 
        dao.getRegistrosPorLote(loteId).map { entities ->
            entities.map { it.toDomain() }
        }
}
