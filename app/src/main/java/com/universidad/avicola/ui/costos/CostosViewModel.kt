package com.universidad.avicola.ui.costos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.universidad.avicola.data.model.*
import com.universidad.avicola.data.repository.CostosCalculator
import com.universidad.avicola.data.repository.CostosRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * CostosViewModel.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/costos/
 */
class CostosViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CostosRepository(app)

    // ── Listas observables ───────────────────────────────────────────────────
    private val _estimaciones = MutableLiveData<List<EstimacionCostos>>(emptyList())
    val estimaciones: LiveData<List<EstimacionCostos>> = _estimaciones

    private val _lotes = MutableLiveData<List<Lote>>(emptyList())
    val lotes: LiveData<List<Lote>> = _lotes

    private val _productos = MutableLiveData<List<ProductoInventario>>(emptyList())
    val productos: LiveData<List<ProductoInventario>> = _productos

    // ── Resultado de cálculo en tiempo real ─────────────────────────────────
    private val _resultado = MutableLiveData<ResultadoCalculo?>(null)
    val resultado: LiveData<ResultadoCalculo?> = _resultado

    // ── Mensajes y estado ────────────────────────────────────────────────────
    private val _mensaje = MutableLiveData("")
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData(false)
    val cargando: LiveData<Boolean> = _cargando

    /**
     * Resultado de la verificación de stock antes de activar producción.
     * - null  → no se ha verificado todavía
     * - Empty → stock suficiente para todo
     * - Non-empty → lista de insumos con stock insuficiente
     */
    private val _stockInsuficiente = MutableLiveData<List<String>?>(null)
    val stockInsuficiente: LiveData<List<String>?> = _stockInsuficiente

    // ── Filtros ───────────────────────────────────────────────────────────────
    private val _estimacionesFiltradas = MutableLiveData<List<EstimacionCostos>>(emptyList())
    val estimacionesFiltradas: LiveData<List<EstimacionCostos>> = _estimacionesFiltradas

    private var filtroEstado: String? = null
    private var filtroLoteId: String? = null

    init {
        cargarEstimaciones()
        cargarLotes()
        cargarProductos()
    }

    // ══════════════════════════════════════════════════════════════════
    //  CARGA
    // ══════════════════════════════════════════════════════════════════

    private fun cargarEstimaciones() {
        viewModelScope.launch {
            repo.obtenerEstimaciones().collectLatest { lista ->
                _estimaciones.value = lista
                aplicarFiltros(lista)
            }
        }
    }

    private fun cargarLotes() {
        viewModelScope.launch {
            repo.obtenerLotesActivos().collectLatest { _lotes.value = it }
        }
    }

    private fun cargarProductos() {
        viewModelScope.launch {
            repo.obtenerProductosInventario().collectLatest { _productos.value = it }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  CÁLCULO EN TIEMPO REAL
    // ══════════════════════════════════════════════════════════════════

    fun recalcular(
        cantidadAves: Int,
        diasCrianza: Int,
        fases: List<FaseAlimentacion>,
        itemsSanitarios: List<ItemSanitario>,
        costosOperativos: List<CostoOperativo>,
        porcentajeMortalidad: Double,
        precioVentaUnitario: Double,
        costoAveInicial: Double = 0.0
    ) {
        if (cantidadAves <= 0) return
        _resultado.value = CostosCalculator.calcular(
            cantidadAves         = cantidadAves,
            diasCrianza          = diasCrianza,
            fases                = fases,
            itemsSanitarios      = itemsSanitarios,
            costosOperativos     = costosOperativos,
            porcentajeMortalidad = porcentajeMortalidad,
            precioVentaUnitario  = precioVentaUnitario,
            costoAveInicial      = costoAveInicial
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  CRUD
    // ══════════════════════════════════════════════════════════════════

    fun guardarEstimacion(estimacion: EstimacionCostos) {
        viewModelScope.launch {
            _cargando.value = true
            val productos = _productos.value ?: emptyList()

            val fasesEnriquecidas = repo.enriquecerFasesConInventario(estimacion.fases, productos)
            val itemsEnriquecidos = repo.enriquecerItemsSanitariosConInventario(
                estimacion.itemsSanitarios, productos
            )
            val resultado = CostosCalculator.calcular(
                cantidadAves         = estimacion.cantidadAves,
                diasCrianza          = estimacion.diasCrianza,
                fases                = fasesEnriquecidas,
                itemsSanitarios      = itemsEnriquecidos,
                costosOperativos     = estimacion.costosOperativos,
                porcentajeMortalidad = estimacion.porcentajeMortalidad,
                precioVentaUnitario  = estimacion.precioVentaUnitario
            )
            val estimacionFinal = estimacion.copy(
                fases                   = fasesEnriquecidas,
                itemsSanitarios         = itemsEnriquecidos,
                costoAlimentacionTotal  = resultado.costoAlimentacion,
                costoSanitarioTotal     = resultado.costoSanitario,
                costoOperativoTotal     = resultado.costoOperativo,
                perdidaMortalidad       = resultado.perdidaMortalidad,
                costoTotal              = resultado.costoTotal,
                costoPorAve             = resultado.costoPorAve,
                ingresoEstimado         = resultado.ingresoEstimado,
                gananciaNeta            = resultado.gananciaNeta,
                roi                     = resultado.roi,
                puntoEquilibrioUnidades = resultado.puntoEquilibrioUnidades,
                alertas                 = resultado.alertas
            )
            repo.guardarEstimacion(estimacionFinal)
                .onSuccess { _mensaje.value = "Estimación guardada correctamente" }
                .onFailure { _mensaje.value = "Error al guardar: ${it.message}" }
            _cargando.value = false
        }
    }

    fun eliminarEstimacion(id: String) {
        viewModelScope.launch {
            repo.eliminarEstimacion(id)
                .onSuccess { _mensaje.value = "Estimación eliminada" }
                .onFailure { _mensaje.value = "Error al eliminar" }
        }
    }

    fun duplicarEstimacion(id: String) {
        viewModelScope.launch {
            repo.duplicarEstimacion(id)
                .onSuccess { _mensaje.value = "Estimación duplicada" }
                .onFailure { _mensaje.value = "Error al duplicar" }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  INTEGRACIÓN FINANZAS
    // ══════════════════════════════════════════════════════════════════

    fun enviarCostoAFinanzas(estimacion: EstimacionCostos) {
        viewModelScope.launch {
            repo.enviarCostoAFinanzas(estimacion)
                .onSuccess { _mensaje.value = "Costo enviado al módulo de Finanzas como gasto proyectado" }
                .onFailure { _mensaje.value = "Error al enviar a Finanzas: ${it.message}" }
        }
    }

    fun registrarCostoReal(estimacion: EstimacionCostos, costoReal: Double) {
        viewModelScope.launch {
            repo.actualizarCostoReal(estimacion, costoReal)
                .onSuccess { _mensaje.value = "Costo real registrado. Estimación completada." }
                .onFailure { _mensaje.value = "Error al registrar costo real" }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ACTIVAR PRODUCCIÓN — con verificación de stock previa
    // ══════════════════════════════════════════════════════════════════

    /**
     * Paso 1: Verificar stock ANTES de mostrar el diálogo de confirmación.
     * El resultado se publica en [stockInsuficiente]:
     *   - Lista vacía  → todo el inventario es suficiente
     *   - Lista con items → hay insumos insuficientes; la Activity los muestra al usuario
     */
    fun verificarStockParaActivar(estimacion: EstimacionCostos) {
        viewModelScope.launch {
            _cargando.value = true
            val productos = _productos.value ?: emptyList()
            val insuficientes = repo.verificarStockParaProduccion(estimacion, productos)
            _stockInsuficiente.value = insuficientes
            _cargando.value = false
        }
    }

    /** Limpia el resultado de la verificación después de que la Activity lo procesó. */
    fun limpiarVerificacionStock() {
        _stockInsuficiente.value = null
    }

    /**
     * Paso 2: Activar producción.
     * Llamar solo después de que el usuario confirmó (con o sin advertencia de stock).
     *
     * @param forzar Si true, activa aunque haya insumos insuficientes
     *               (descuenta solo los que alcancen y omite los demás).
     */
    fun activarProduccion(estimacion: EstimacionCostos, forzar: Boolean = false) {
        viewModelScope.launch {
            _cargando.value = true
            val productos = _productos.value ?: emptyList()

            // Si no se fuerza, verificar una última vez
            if (!forzar) {
                val insuficientes = repo.verificarStockParaProduccion(estimacion, productos)
                if (insuficientes.isNotEmpty()) {
                    // La Activity debe haber manejado esto antes de llegar aquí,
                    // pero lo capturamos por seguridad
                    _stockInsuficiente.value = insuficientes
                    _cargando.value = false
                    return@launch
                }
            }

            repo.descontarInventarioParaProduccion(estimacion, productos)
                .onSuccess {
                    guardarEstimacion(estimacion.copy(estado = EstadoEstimacion.ACTIVA.name))
                    _mensaje.value = "✔ Producción activada. Inventario actualizado."
                }
                .onFailure {
                    _mensaje.value = "Error al activar producción: ${it.message}"
                }
            _cargando.value = false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  FILTROS
    // ══════════════════════════════════════════════════════════════════

    fun setFiltroEstado(estado: String?) {
        filtroEstado = estado
        aplicarFiltros(_estimaciones.value ?: emptyList())
    }

    fun setFiltroLote(loteId: String?) {
        filtroLoteId = loteId
        aplicarFiltros(_estimaciones.value ?: emptyList())
    }

    fun aplicarFiltros(lista: List<EstimacionCostos>) {
        var filtrada = lista
        filtroEstado?.let { e -> filtrada = filtrada.filter { it.estado == e } }
        filtroLoteId?.let { l -> filtrada = filtrada.filter { it.loteId == l } }
        _estimacionesFiltradas.value = filtrada
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS UI
    // ══════════════════════════════════════════════════════════════════

    fun iniciarNuevaEstimacion(): EstimacionCostos = EstimacionCostos(
        fases            = CostosCalculator.fasesDefault(TipoAveEstimacion.ENGORDE),
        costosOperativos = CostosCalculator.costosOperativosDefault()
    )

    fun fasesDefaultParaTipo(tipo: TipoAveEstimacion): List<FaseAlimentacion> =
        CostosCalculator.fasesDefault(tipo)

    data class MetricasResumen(
        val totalEstimaciones: Int,
        val roiPromedio: Double,
        val costoTotalAcumulado: Double,
        val estimacionesRentables: Int
    )

    fun calcularMetricas(lista: List<EstimacionCostos>): MetricasResumen {
        val activas = lista.filter { it.estado != EstadoEstimacion.ARCHIVADA.name }
        return MetricasResumen(
            totalEstimaciones     = activas.size,
            roiPromedio           = if (activas.isEmpty()) 0.0 else activas.map { it.roi }.average(),
            costoTotalAcumulado   = activas.sumOf { it.costoTotal },
            estimacionesRentables = activas.count { it.isRentable() }
        )
    }
}