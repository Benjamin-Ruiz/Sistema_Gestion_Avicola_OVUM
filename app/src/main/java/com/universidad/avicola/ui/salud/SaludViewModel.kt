package com.universidad.avicola.ui.salud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.universidad.avicola.data.model.*
import com.universidad.avicola.data.repository.SaludRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  SaludViewModel.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/ui/salud/
// ─────────────────────────────────────────────────────────────────────────────

class SaludViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SaludRepository(app)

    // ── Lotes ───────────────────────────────────────────────────────────────
    private val _lotes = MutableLiveData<List<Lote>>(emptyList())
    val lotes: LiveData<List<Lote>> = _lotes

    // ── Estados sanitarios calculados por lote ──────────────────────────────
    private val _estadosSanitarios = MutableLiveData<List<EstadoSanitarioLote>>(emptyList())
    val estadosSanitarios: LiveData<List<EstadoSanitarioLote>> = _estadosSanitarios

    // ── Registros médicos ───────────────────────────────────────────────────
    private val _registros = MutableLiveData<List<RegistroMedico>>(emptyList())
    val registros: LiveData<List<RegistroMedico>> = _registros

    private val _registrosUrgentes = MutableLiveData<List<RegistroMedico>>(emptyList())
    val registrosUrgentes: LiveData<List<RegistroMedico>> = _registrosUrgentes

    // ── Vacunaciones ────────────────────────────────────────────────────────
    private val _vacunaciones = MutableLiveData<List<Vacunacion>>(emptyList())
    val vacunaciones: LiveData<List<Vacunacion>> = _vacunaciones

    private val _vacunacionesPendientes = MutableLiveData<List<Vacunacion>>(emptyList())
    val vacunacionesPendientes: LiveData<List<Vacunacion>> = _vacunacionesPendientes

    // ── Productos médicos del inventario ────────────────────────────────────
    private val _productosMedicos = MutableLiveData<List<ProductoInventario>>(emptyList())
    val productosMedicos: LiveData<List<ProductoInventario>> = _productosMedicos

    // ── Diagnóstico asistido ────────────────────────────────────────────────
    private val _sugerencias = MutableLiveData<List<SugerenciaDiagnostico>>(emptyList())
    val sugerencias: LiveData<List<SugerenciaDiagnostico>> = _sugerencias

    // ── Estado UI ───────────────────────────────────────────────────────────
    private val _mensaje = MutableLiveData("")
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData(false)
    val cargando: LiveData<Boolean> = _cargando

    // ── Filtro activo en la lista ────────────────────────────────────────────
    private val _tabActivo = MutableLiveData(TabSalud.LOTES)
    val tabActivo: LiveData<TabSalud> = _tabActivo

    enum class TabSalud { LOTES, REGISTROS, VACUNAS, ALERTAS }

    init {
        cargarLotes()
        cargarRegistros()
        cargarVacunaciones()
        cargarProductosMedicos()
    }

    // ══════════════════════════════════════════════════════════════════
    //  CARGA
    // ══════════════════════════════════════════════════════════════════

    private fun cargarLotes() {
        viewModelScope.launch {
            repo.obtenerLotesActivos().collectLatest { lotes ->
                _lotes.value = lotes
                recalcularEstadosSanitarios(lotes)
            }
        }
    }

    private fun recalcularEstadosSanitarios(lotes: List<Lote>) {
        viewModelScope.launch {
            val estados = lotes.map { lote -> repo.calcularEstadoSanitario(lote) }
            _estadosSanitarios.value = estados
        }
    }

    private fun cargarRegistros() {
        viewModelScope.launch {
            repo.obtenerRegistros().collectLatest { _registros.value = it }
        }
        viewModelScope.launch {
            repo.obtenerRegistrosUrgentes().collectLatest { _registrosUrgentes.value = it }
        }
    }

    private fun cargarVacunaciones() {
        viewModelScope.launch {
            repo.obtenerVacunaciones().collectLatest { _vacunaciones.value = it }
        }
        viewModelScope.launch {
            repo.obtenerVacunacionesPendientes().collectLatest { _vacunacionesPendientes.value = it }
        }
    }

    private fun cargarProductosMedicos() {
        viewModelScope.launch {
            repo.obtenerProductosMedicos().collectLatest { _productosMedicos.value = it }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  REGISTROS MÉDICOS
    // ══════════════════════════════════════════════════════════════════

    fun guardarRegistro(registro: RegistroMedico) {
        viewModelScope.launch {
            _cargando.value = true
            repo.guardarRegistro(registro)
                .onSuccess {
                    _mensaje.value = "Registro médico guardado"
                    recalcularEstadosSanitarios(_lotes.value ?: emptyList())
                }
                .onFailure { _mensaje.value = "Error al guardar: ${it.message}" }
            _cargando.value = false
        }
    }

    fun marcarResuelta(id: String) {
        viewModelScope.launch {
            repo.marcarResuelta(id)
                .onSuccess {
                    _mensaje.value = "Caso marcado como resuelto"
                    recalcularEstadosSanitarios(_lotes.value ?: emptyList())
                }
                .onFailure { _mensaje.value = "Error al actualizar" }
        }
    }

    fun eliminarRegistro(id: String) {
        viewModelScope.launch {
            repo.eliminarRegistro(id)
                .onSuccess { _mensaje.value = "Registro eliminado" }
                .onFailure { _mensaje.value = "Error al eliminar" }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  VACUNACIONES
    // ══════════════════════════════════════════════════════════════════

    fun guardarVacunacion(vacunacion: Vacunacion) {
        viewModelScope.launch {
            _cargando.value = true
            repo.guardarVacunacion(vacunacion)
                .onSuccess {
                    _mensaje.value = "Vacunación guardada"
                    recalcularEstadosSanitarios(_lotes.value ?: emptyList())
                }
                .onFailure { _mensaje.value = "Error al guardar vacunación: ${it.message}" }
            _cargando.value = false
        }
    }

    fun aplicarVacuna(vacunacion: Vacunacion) {
        viewModelScope.launch {
            _cargando.value = true
            val productos = _productosMedicos.value ?: emptyList()
            repo.marcarVacunacionAplicada(vacunacion, productos)
                .onSuccess {
                    _mensaje.value = "Vacuna aplicada. Inventario e historial actualizados."
                    recalcularEstadosSanitarios(_lotes.value ?: emptyList())
                }
                .onFailure { _mensaje.value = "Error al aplicar vacuna: ${it.message}" }
            _cargando.value = false
        }
    }

    fun eliminarVacunacion(id: String) {
        viewModelScope.launch {
            repo.eliminarVacunacion(id)
                .onSuccess { _mensaje.value = "Vacunación eliminada" }
                .onFailure { _mensaje.value = "Error al eliminar" }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  DIAGNÓSTICO ASISTIDO
    // ══════════════════════════════════════════════════════════════════

    fun analizarSintomas(sintomas: List<String>) {
        _sugerencias.value = DiagnosticoAsistido.sugerir(sintomas)
    }

    fun limpiarSugerencias() { _sugerencias.value = emptyList() }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS UI
    // ══════════════════════════════════════════════════════════════════

    fun setTab(tab: TabSalud) { _tabActivo.value = tab }

    fun registrosPorLote(loteId: String): List<RegistroMedico> =
        _registros.value?.filter { it.loteId == loteId } ?: emptyList()

    fun vacunacionesPorLote(loteId: String): List<Vacunacion> =
        _vacunaciones.value?.filter { it.loteId == loteId } ?: emptyList()

    fun estadoSanitarioDeLote(loteId: String): EstadoSanitarioLote? =
        _estadosSanitarios.value?.firstOrNull { it.loteId == loteId }

    data class ResumenSalud(
        val lotesEnRiesgo: Int,
        val casosUrgentes: Int,
        val vacunasPendientes: Int,
        val costoSanitarioTotal: Double
    )

    fun calcularResumen(): ResumenSalud {
        val estados = _estadosSanitarios.value ?: emptyList()
        return ResumenSalud(
            lotesEnRiesgo     = estados.count { it.isEnRiesgo() },
            casosUrgentes     = _registrosUrgentes.value?.size ?: 0,
            vacunasPendientes = _vacunacionesPendientes.value?.size ?: 0,
            costoSanitarioTotal = estados.sumOf { it.costoSanitarioTotal }
        )
    }
}