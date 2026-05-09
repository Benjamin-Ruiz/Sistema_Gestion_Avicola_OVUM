package com.universidad.avicola.ui.aves

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.universidad.avicola.data.model.Lote
import com.universidad.avicola.data.repository.AvesRepository
import kotlinx.coroutines.launch

class AvesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AvesRepository(application)

    val lotesActivos = repository.getLotesActivos().asLiveData()

    fun crearLote(lote: Lote) {
        viewModelScope.launch {
            repository.crearLote(lote)
        }
    }

    fun registrarEvento(loteId: String, mortalidad: Int, descarte: Int, peso: Double, obs: String) {
        viewModelScope.launch {
            repository.registrarBajasYPesaje(loteId, mortalidad, descarte, peso, obs)
        }
    }

    fun cerrarLote(lote: Lote) {
        viewModelScope.launch {
            repository.cerrarLote(lote)
        }
    }

    fun eliminarLote(id: String) {
        viewModelScope.launch {
            repository.eliminarLote(id)
        }
    }

    fun getHistorial(loteId: String) = repository.getHistorialLote(loteId).asLiveData()
}
